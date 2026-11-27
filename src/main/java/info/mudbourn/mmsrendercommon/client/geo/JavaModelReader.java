package info.mudbourn.mmsrendercommon.client.geo;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link GeoModelData} by running the {@code createBodyLayer()} method of a compiled
 * vanilla-style entity model class.
 *
 * <p>The method is interpreted over its bytecode, so nothing of the class is loaded or
 * executed. Only straight-line code over the model builder calls is understood: constants,
 * {@code CubeDeformation}, {@code CubeListBuilder}, {@code PartPose}, {@code MeshDefinition},
 * {@code PartDefinition.addOrReplaceChild} and {@code LayerDefinition.create}. Anything else
 * throws rather than guessing.
 *
 * <p>Java model space (Y down, offsets from the parent part) maps to Bedrock space (Y up, feet
 * at zero) as geo files from Blockbench do: pivots are absolute, {@code y} becomes
 * {@code 24 - y}, and part rotations carry over in degrees. The head, arms and a root-level
 * {@code hat} follow the Bedrock humanoid rig: the head and arms hang from {@code body} and the
 * hat becomes {@code headwear} under {@code head}.
 */
public final class JavaModelReader {

    private static final String BUILDERS = "net/minecraft/client/model/geom/builders/";
    private static final String CUBE_DEFORMATION = BUILDERS + "CubeDeformation";
    private static final String CUBE_LIST_BUILDER = BUILDERS + "CubeListBuilder";
    private static final String LAYER_DEFINITION = BUILDERS + "LayerDefinition";
    private static final String MESH_DEFINITION = BUILDERS + "MeshDefinition";
    private static final String PART_DEFINITION = BUILDERS + "PartDefinition";
    private static final String PART_POSE = "net/minecraft/client/model/geom/PartPose";

    private static final String BODY_LAYER = "createBodyLayer";
    private static final String ROOT = "";
    private static final Map<String, String> RENAMES = Map.of("hat", "headwear");
    private static final Map<String, String> RIG_PARENTS = Map.of(
            "head", "body",
            "left_arm", "body",
            "right_arm", "body",
            "headwear", "head"
    );

    /** One cube as the Java builder records it. */
    private record JavaCube(float u, float v, boolean mirror, float[] box, float grow) {}

    /** A part pose: offset from the parent and rotation in radians. */
    private record Pose(float x, float y, float z, float rx, float ry, float rz) {}

    /** A constructed object that a {@code new} slot on the stack still refers to. */
    private static final class Pending {

        private Object value;
    }

    private static final class Builder {

        private final List<JavaCube> cubes = new ArrayList<>();
        private float u;
        private float v;
        private boolean mirror;
    }

    private static final class Part {

        private final String name;
        private final Pose pose;
        private final List<JavaCube> cubes;
        private final Map<String, Part> children = new LinkedHashMap<>();

        private Part(String name, Pose pose, List<JavaCube> cubes) {
            this.name = name;
            this.pose = pose;
            this.cubes = cubes;
        }
    }

    private record Layer(Part root, int width, int height) {}

    private JavaModelReader() {
    }

    /** Reads the model {@code createBodyLayer()} of a class file builds, or throws when it cannot be understood. */
    public static GeoModelData read(byte[] classFile) {
        ClassNode node = new ClassNode();
        new ClassReader(classFile).accept(node, ClassReader.SKIP_FRAMES);
        for (MethodNode method : node.methods) {
            if (method.name.equals(BODY_LAYER) && (method.access & Opcodes.ACC_STATIC) != 0) {
                return convert(interpret(method));
            }
        }
        throw new IllegalArgumentException("No static " + BODY_LAYER + " in " + node.name);
    }

    private static Layer interpret(MethodNode method) {
        Deque<Object> stack = new ArrayDeque<>();
        Map<Integer, Object> locals = new HashMap<>();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode < 0) {
                continue;
            }
            switch (insn) {
                case InsnNode simple -> {
                    if (simple.getOpcode() == Opcodes.ARETURN) {
                        return (Layer) unwrap(stack.pop());
                    }
                    simpleInsn(simple.getOpcode(), stack);
                }
                case IntInsnNode push when opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH ->
                        stack.push(push.operand);
                case LdcInsnNode ldc -> stack.push(ldc.cst);
                case VarInsnNode var when opcode == Opcodes.ASTORE -> locals.put(var.var, stack.pop());
                case VarInsnNode var when opcode == Opcodes.ALOAD -> stack.push(locals.get(var.var));
                case TypeInsnNode type when opcode == Opcodes.NEW -> stack.push(new Pending());
                case MethodInsnNode call -> invoke(call, stack);
                default -> throw unsupported(insn);
            }
        }
        throw new IllegalArgumentException("createBodyLayer returns nothing");
    }

    private static void simpleInsn(int opcode, Deque<Object> stack) {
        switch (opcode) {
            case Opcodes.ICONST_M1, Opcodes.ICONST_0, Opcodes.ICONST_1, Opcodes.ICONST_2,
                 Opcodes.ICONST_3, Opcodes.ICONST_4, Opcodes.ICONST_5 -> stack.push(opcode - Opcodes.ICONST_0);
            case Opcodes.FCONST_0, Opcodes.FCONST_1, Opcodes.FCONST_2 ->
                    stack.push((float) (opcode - Opcodes.FCONST_0));
            case Opcodes.I2F -> stack.push(((Integer) stack.pop()).floatValue());
            case Opcodes.DUP -> stack.push(stack.peek());
            default -> throw new IllegalArgumentException("Unsupported opcode " + opcode);
        }
    }

    private static void invoke(MethodInsnNode call, Deque<Object> stack) {
        Type[] types = Type.getArgumentTypes(call.desc);
        Object[] args = new Object[types.length];
        for (int i = types.length - 1; i >= 0; i--) {
            args[i] = unwrap(stack.pop());
        }
        String key = call.owner + "." + call.name + call.desc;
        if (call.getOpcode() == Opcodes.INVOKESPECIAL) {
            constructed(key, args, (Pending) stack.pop());
            return;
        }
        Object receiver = call.getOpcode() == Opcodes.INVOKESTATIC ? null : unwrap(stack.pop());
        stack.push(call(key, receiver, args));
    }

    private static void constructed(String key, Object[] args, Pending target) {
        target.value = switch (key) {
            case CUBE_DEFORMATION + ".<init>(F)V" -> args[0];
            case MESH_DEFINITION + ".<init>()V" -> new Part(ROOT, new Pose(0, 0, 0, 0, 0, 0), List.of());
            default -> throw new IllegalArgumentException("Unsupported constructor " + key);
        };
    }

    private static Object call(String key, Object receiver, Object[] args) {
        switch (key) {
            case CUBE_LIST_BUILDER + ".create()L" + CUBE_LIST_BUILDER + ";":
                return new Builder();
            case CUBE_LIST_BUILDER + ".texOffs(II)L" + CUBE_LIST_BUILDER + ";":
                ((Builder) receiver).u = (Integer) args[0];
                ((Builder) receiver).v = (Integer) args[1];
                return receiver;
            case CUBE_LIST_BUILDER + ".mirror()L" + CUBE_LIST_BUILDER + ";":
                ((Builder) receiver).mirror = true;
                return receiver;
            case CUBE_LIST_BUILDER + ".mirror(Z)L" + CUBE_LIST_BUILDER + ";":
                ((Builder) receiver).mirror = (Integer) args[0] != 0;
                return receiver;
            case CUBE_LIST_BUILDER + ".addBox(FFFFFFL" + CUBE_DEFORMATION + ";)L" + CUBE_LIST_BUILDER + ";":
                Builder builder = (Builder) receiver;
                float[] box = new float[6];
                for (int i = 0; i < 6; i++) {
                    box[i] = (Float) args[i];
                }
                builder.cubes.add(new JavaCube(builder.u, builder.v, builder.mirror, box, (Float) args[6]));
                return receiver;
            case PART_POSE + ".offset(FFF)L" + PART_POSE + ";":
                return new Pose((Float) args[0], (Float) args[1], (Float) args[2], 0, 0, 0);
            case PART_POSE + ".offsetAndRotation(FFFFFF)L" + PART_POSE + ";":
                return new Pose(
                        (Float) args[0],
                        (Float) args[1],
                        (Float) args[2],
                        (Float) args[3],
                        (Float) args[4],
                        (Float) args[5]
                );
            case MESH_DEFINITION + ".getRoot()L" + PART_DEFINITION + ";":
                return receiver;
            case PART_DEFINITION + ".addOrReplaceChild(Ljava/lang/String;L" + CUBE_LIST_BUILDER
                    + ";L" + PART_POSE + ";)L" + PART_DEFINITION + ";":
                Part child = new Part((String) args[0], (Pose) args[2], ((Builder) args[1]).cubes);
                ((Part) receiver).children.put(child.name, child);
                return child;
            case LAYER_DEFINITION + ".create(L" + MESH_DEFINITION + ";II)L" + LAYER_DEFINITION + ";":
                return new Layer((Part) args[0], (Integer) args[1], (Integer) args[2]);
            default:
                throw new IllegalArgumentException("Unsupported call " + key);
        }
    }

    private static Object unwrap(Object value) {
        return value instanceof Pending pending ? pending.value : value;
    }

    private static IllegalArgumentException unsupported(AbstractInsnNode insn) {
        return new IllegalArgumentException(
                "Unsupported instruction " + insn.getClass().getSimpleName() + " " + insn.getOpcode()
        );
    }

    private static GeoModelData convert(Layer layer) {
        List<GeoModelData.Bone> bones = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Part part : layer.root().children.values()) {
            addBone(part, null, new float[] {0.0F, 0.0F, 0.0F}, bones, names);
        }
        return new GeoModelData(layer.width(), layer.height(), bones);
    }

    private static void addBone(Part part, String parent, float[] parentPivot, List<GeoModelData.Bone> out,
                                Set<String> names) {
        String name = RENAMES.getOrDefault(part.name, part.name);
        String boneParent = parent == null ? RIG_PARENTS.get(name) : parent;
        if (!names.add(name)) {
            throw new IllegalArgumentException("Duplicate part name " + name);
        }
        Pose pose = part.pose;
        float[] pivot = {parentPivot[0] + pose.x(), parentPivot[1] + pose.y(), parentPivot[2] + pose.z()};
        float[] rotation = pose.rx() == 0.0F && pose.ry() == 0.0F && pose.rz() == 0.0F
                ? null
                : new float[] {
                        (float) Math.toDegrees(pose.rx()),
                        (float) Math.toDegrees(pose.ry()),
                        (float) Math.toDegrees(pose.rz())
                };
        List<GeoModelData.Cube> cubes = new ArrayList<>();
        for (JavaCube cube : part.cubes) {
            cubes.add(new GeoModelData.Cube(
                    new float[] {
                            pivot[0] + cube.box()[0],
                            24.0F - (pivot[1] + cube.box()[1] + cube.box()[4]),
                            pivot[2] + cube.box()[2]
                    },
                    new float[] {cube.box()[3], cube.box()[4], cube.box()[5]},
                    null,
                    null,
                    cube.grow(),
                    cube.mirror(),
                    new GeoModelData.BoxUv(cube.u(), cube.v())
            ));
        }
        out.add(new GeoModelData.Bone(
                name,
                boneParent,
                new float[] {pivot[0], 24.0F - pivot[1], pivot[2]},
                rotation,
                cubes
        ));
        for (Part child : part.children.values()) {
            addBone(child, name, pivot, out, names);
        }
    }
}
