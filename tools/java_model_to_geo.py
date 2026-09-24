#!/usr/bin/env python3
"""Ports Java entity-model armour into geo armour for mms-render-common.

Reads the LayerDefinition code of per-slot Java armour models (decompiled
intermediary, Yarn or Mojang names), evaluates the builder calls, and writes:

  geo/armor/<set>.geo.json             every slot merged into one GeckoLib-layout geo
  geo/armor/<set>_slim.geo.json        the same from the slim layer methods, when any
  animations/armor/<set>.animation.json  the manifest's procedural curves as Molang
  geo_armor/<asset>.json               one definition per skin, keyed by asset id

Every cube is checked against vanilla's own ModelPart math before anything is
written: the tool bakes the geo exactly as GeoModel and GeoArmorRenderer do and
fails if any vertex or UV drifts from where the Java model draws it.

Usage:
  java_model_to_geo.py MANIFEST --sources DIR --out ASSETS_DIR

The manifest is JSON:

  {
    "namespace": "armoroftheages",
    "helpers": ["client/models/ArmorModel.java"],
    "sets": {
      "raijin_armor": {
        "slots": {"head": "client/models/raijin_armor/HeadRaijinArmorModel.java"},
        "layer_method": "createLayerDefinition",
        "slim_method": "createSlimLayerDefinition",
        "texture": "armoroftheages:textures/models/armor/{skin}raijin_armor.png",
        "slim_texture": "armoroftheages:textures/models/armor/{skin}raijin_armor_slim.png",
        "skins": ["", "silver_"],
        "animations": {"head": {"flyA": {"y": "-15 + sinpi(age / 60 + 1)"}}}
      }
    }
  }

Slots are head, chest, legs and feet. Animation keys are x, y, z (the part's
offset, Java pixels) and x_rot, y_rot, z_rot (radians), written as absolute
Java values; the tool subtracts the rest pose and converts to Bedrock. A part
with "relative": true gives offsets from its rest pose instead, which also
holds for a slim model that rests differently. The
expression language is Molang plus: age (ticks), sinpi(v), cospi(v), abs, min,
max, mod, pi, <part>.x_rot / y_rot / z_rot for the wearer's head, body,
right_arm, left_arm, right_leg and left_leg (radians), head_yaw and head_pitch
(degrees), riding and crouching (0 or 1).
"""

import argparse
import json
import math
import os
import re
import sys

SLOTS = ("head", "chest", "legs", "feet")

ROOTS = ("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg")

ANCHORS = {
    "head": (0.0, 0.0, 0.0),
    "hat": (0.0, 0.0, 0.0),
    "body": (0.0, 0.0, 0.0),
    "right_arm": (-5.0, 2.0, 0.0),
    "left_arm": (5.0, 2.0, 0.0),
    "right_leg": (-2.0, 12.0, 0.0),
    "left_leg": (2.0, 12.0, 0.0),
}

REST_POSE = {
    "head": (0.0, 0.0, 0.0),
    "hat": (0.0, 0.0, 0.0),
    "body": (0.0, 0.0, 0.0),
    "right_arm": (-5.0, 2.0, 0.0),
    "left_arm": (5.0, 2.0, 0.0),
    "right_leg": (-1.9, 12.0, 0.0),
    "left_leg": (1.9, 12.0, 0.0),
}

SEGMENT_OFFSETS = {
    "head": (0.0, 0.0),
    "hat": (0.0, 0.0),
    "body": (0.0, 0.0),
    "right_arm": (5.0, 2.0),
    "left_arm": (-5.0, 2.0),
    "right_leg": (2.0, 12.0),
    "left_leg": (-2.0, 12.0),
}

BONES = {
    ("head", "head"): "armorHead",
    ("head", "hat"): "armorHead",
    ("chest", "body"): "armorBody",
    ("chest", "right_arm"): "armorRightArm",
    ("chest", "left_arm"): "armorLeftArm",
    ("legs", "body"): "armorLegsBody",
    ("legs", "right_leg"): "armorRightLeg",
    ("legs", "left_leg"): "armorLeftLeg",
    ("feet", "right_leg"): "armorRightBoot",
    ("feet", "left_leg"): "armorLeftBoot",
}

OPS = {
    "method_32117": "add_child",
    "addOrReplaceChild": "add_child",
    "addChild": "add_child",
    "method_32116": "get_child",
    "getChild": "get_child",
    "method_32111": "get_root",
    "getRoot": "get_root",
    "method_32108": "cubes",
    "create": "create",
    "method_32101": "tex_offs",
    "texOffs": "tex_offs",
    "uv": "tex_offs",
    "method_32096": "mirror",
    "method_32106": "mirror",
    "mirror": "mirror",
    "mirrored": "mirror",
    "method_32098": "add_box",
    "method_32097": "add_box",
    "method_32100": "add_box",
    "method_32102": "add_box",
    "method_32103": "add_box",
    "method_32104": "add_box",
    "method_32105": "add_box",
    "addBox": "add_box",
    "cuboid": "add_box",
    "method_32090": "offset",
    "offset": "offset",
    "pivot": "offset",
    "method_32091": "offset_and_rotation",
    "offsetAndRotation": "offset_and_rotation",
    "of": "offset_and_rotation",
    "method_32092": "rotation",
    "rotation": "rotation",
    "method_32110": "layer",
}

TYPES = {
    "class_5609": "Mesh",
    "MeshDefinition": "Mesh",
    "ModelData": "Mesh",
    "class_5605": "Deformation",
    "CubeDeformation": "Deformation",
    "Dilation": "Deformation",
    "class_5606": "CubeListBuilder",
    "CubeListBuilder": "CubeListBuilder",
    "ModelPartBuilder": "CubeListBuilder",
    "class_5603": "PartPose",
    "PartPose": "PartPose",
    "ModelTransform": "PartPose",
    "class_5607": "LayerDefinition",
    "LayerDefinition": "LayerDefinition",
    "TexturedModelData": "LayerDefinition",
}

CONSTANTS = {
    ("Deformation", "field_27715"): 0.0,
    ("Deformation", "NONE"): 0.0,
    ("PartPose", "field_27701"): (0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    ("PartPose", "ZERO"): (0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
    ("PartPose", "NONE"): (0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
}

TOKEN = re.compile(r"""
    (?P<space>\s+|//[^\n]*|/\*.*?\*/)
  | (?P<number>\d+\.\d*(?:[eE][-+]?\d+)?[fFdD]?|\.\d+[fFdD]?|\d+[fFdDlL]?)
  | (?P<string>"(?:\\.|[^"\\])*")
  | (?P<name>[A-Za-z_$][\w$]*)
  | (?P<punct>[(){}\[\],;.=+\-*/<>?:!&|%])
""", re.VERBOSE | re.DOTALL)


class PortError(Exception):
    pass


class Part:
    def __init__(self, cubes, pose):
        self.cubes = cubes
        self.pose = pose
        self.children = {}

    def add_child(self, name, cubes, pose):
        part = Part(cubes, pose)
        old = self.children.get(name)
        if old is not None:
            part.children.update(old.children)
        self.children[name] = part
        return part


class Mesh:
    def __init__(self):
        self.root = Part([], (0.0,) * 6)


class Builder:
    def __init__(self):
        self.u = 0
        self.v = 0
        self.mirror = False
        self.cubes = []


class Parser:
    def __init__(self, source, where):
        self.tokens = []
        self.where = where
        position = 0
        while position < len(source):
            match = TOKEN.match(source, position)
            if match is None:
                raise PortError(f"{where}: cannot tokenize at {source[position:position + 30]!r}")
            position = match.end()
            if match.lastgroup != "space":
                self.tokens.append((match.lastgroup, match.group()))
        self.index = 0

    def peek(self, offset=0):
        index = self.index + offset
        return self.tokens[index] if index < len(self.tokens) else ("eof", "")

    def take(self, value=None):
        token = self.peek()
        if value is not None and token[1] != value:
            raise PortError(f"{self.where}: expected {value!r}, got {token[1]!r}")
        self.index += 1
        return token

    def accept(self, value):
        if self.peek()[1] == value:
            self.index += 1
            return True
        return False


class Source:
    """One Java file: its static methods, callable by name."""

    def __init__(self, path):
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        self.path = path
        self.methods = {}
        pattern = re.compile(r"static\s+[\w<>\[\], ?]+?\s+(\w+)\s*\(([^)]*)\)\s*\{")
        for match in pattern.finditer(text):
            body_start = match.end()
            depth = 1
            index = body_start
            while depth:
                if text[index] == "{":
                    depth += 1
                elif text[index] == "}":
                    depth -= 1
                index += 1
            params = [param.split()[-1] for param in match.group(2).split(",") if param.strip()]
            self.methods[match.group(1)] = (params, text[body_start:index - 1])


class Evaluator:
    def __init__(self, sources):
        self.sources = sources

    def call(self, name, args):
        for source in self.sources:
            if name in source.methods:
                params, body = source.methods[name]
                if len(params) != len(args):
                    raise PortError(f"{name} takes {len(params)} arguments, got {len(args)}")
                return self.run(Parser(body, f"{source.path}:{name}"), dict(zip(params, args)))
        raise PortError(f"unknown method {name}")

    def run(self, parser, scope):
        while parser.peek()[0] != "eof":
            if parser.accept("return"):
                value = self.expr(parser, scope)
                parser.take(";")
                return value
            if parser.peek()[0] == "name" and parser.peek(1)[0] == "name" and parser.peek(2)[1] == "=":
                parser.take()
                name = parser.take()[1]
                parser.take("=")
                scope[name] = self.expr(parser, scope)
            elif parser.peek()[0] == "name" and parser.peek(1)[1] == "=":
                name = parser.take()[1]
                parser.take("=")
                scope[name] = self.expr(parser, scope)
            else:
                self.expr(parser, scope)
            parser.take(";")
        return None

    def expr(self, parser, scope):
        value = self.term(parser, scope)
        while parser.peek()[1] in ("+", "-"):
            op = parser.take()[1]
            right = self.term(parser, scope)
            value = value + right if op == "+" else value - right
        return value

    def term(self, parser, scope):
        value = self.unary(parser, scope)
        while parser.peek()[1] in ("*", "/"):
            op = parser.take()[1]
            right = self.unary(parser, scope)
            value = value * right if op == "*" else value / right
        return value

    def unary(self, parser, scope):
        if parser.accept("-"):
            return -self.unary(parser, scope)
        return self.postfix(parser, scope)

    def postfix(self, parser, scope):
        value = self.primary(parser, scope)
        while parser.accept("."):
            name = parser.take()[1]
            args = self.args(parser, scope) if parser.peek()[1] == "(" else None
            value = self.member(value, name, args, parser)
        return value

    def args(self, parser, scope):
        parser.take("(")
        values = []
        if not parser.accept(")"):
            values.append(self.expr(parser, scope))
            while parser.accept(","):
                values.append(self.expr(parser, scope))
            parser.take(")")
        return values

    def primary(self, parser, scope):
        kind, text = parser.take()
        if kind == "number":
            return float(text.rstrip("fFdDlL"))
        if kind == "string":
            return json.loads(text)
        if text == "(" and parser.peek()[1] in ("float", "double", "int") and parser.peek(1)[1] == ")":
            parser.take()
            parser.take(")")
            return self.unary(parser, scope)
        if text == "(":
            value = self.expr(parser, scope)
            parser.take(")")
            return value
        if text in ("true", "false"):
            return text == "true"
        if text == "new":
            type_name = TYPES.get(parser.take()[1])
            args = self.args(parser, scope)
            if type_name == "Mesh":
                return Mesh()
            if type_name == "Deformation":
                return args[0] if len(args) == 1 else tuple(args)
            raise PortError(f"{parser.where}: cannot construct {type_name}")
        if text == "Math" and parser.accept("."):
            name = parser.take()[1]
            if name == "PI":
                return math.pi
            raise PortError(f"{parser.where}: unsupported Math.{name}")
        if kind == "name" and text in scope:
            return scope[text]
        if kind == "name" and text in TYPES:
            return ("type", TYPES[text])
        if kind == "name" and parser.peek()[1] == "(":
            return self.call(text, self.args(parser, scope))
        raise PortError(f"{parser.where}: unknown name {text!r}")

    def member(self, target, name, args, parser):
        op = OPS.get(name)
        if isinstance(target, tuple) and target[:1] == ("type",):
            if args is None:
                key = (target[1], name)
                if key not in CONSTANTS:
                    raise PortError(f"{parser.where}: unknown constant {target[1]}.{name}")
                return CONSTANTS[key]
            if target[1] == "CubeListBuilder" and op in ("cubes", "create"):
                return Builder()
            if target[1] == "PartPose" and op == "offset":
                return (*args, 0.0, 0.0, 0.0)
            if target[1] == "PartPose" and op == "offset_and_rotation":
                return tuple(args)
            if target[1] == "PartPose" and op == "rotation":
                return (0.0, 0.0, 0.0, *args)
            if target[1] == "LayerDefinition" and op == "layer":
                return (args[0], int(args[1]), int(args[2]))
            raise PortError(f"{parser.where}: unknown static {target[1]}.{name}")
        if isinstance(target, Mesh) and op == "get_root":
            return target.root
        if isinstance(target, Part) and op == "add_child":
            builder = args[1]
            return target.add_child(args[0], list(builder.cubes), tuple(args[2]))
        if isinstance(target, Part) and op == "get_child":
            return target.children[args[0]]
        if isinstance(target, Builder):
            return self.build(target, op, args, parser)
        raise PortError(f"{parser.where}: unknown call .{name} on {type(target).__name__}")

    def build(self, builder, op, args, parser):
        if op == "tex_offs":
            builder.u, builder.v = int(args[0]), int(args[1])
        elif op == "mirror":
            builder.mirror = bool(args[0]) if args else True
        elif op == "add_box":
            uv = (builder.u, builder.v)
            if args and isinstance(args[0], str):
                args = args[1:]
                if len(args) in (8, 9):
                    uv = (int(args[-2]), int(args[-1]))
                    args = args[:-2]
            box = args[:6]
            grow = 0.0
            mirror = builder.mirror
            rest = args[6:]
            if len(rest) == 1 and isinstance(rest[0], bool):
                mirror = rest[0]
            elif len(rest) == 1:
                grow = rest[0]
            elif rest:
                raise PortError(f"{parser.where}: unsupported addBox overload")
            if not isinstance(grow, tuple):
                grow = (grow, grow, grow)
            builder.cubes.append({
                "origin": tuple(box[:3]),
                "size": tuple(box[3:6]),
                "grow": grow,
                "mirror": mirror,
                "uv": uv,
            })
        else:
            raise PortError(f"{parser.where}: unsupported builder call {op}")
        return builder


def rotation_zyx(rx, ry, rz):
    cx, sx = math.cos(rx), math.sin(rx)
    cy, sy = math.cos(ry), math.sin(ry)
    cz, sz = math.cos(rz), math.sin(rz)
    rot_x = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    rot_y = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rot_z = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return matmul(matmul(rot_z, rot_y), rot_x)


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(matrix, vector):
    return tuple(sum(matrix[i][k] * vector[k] for k in range(3)) for i in range(3))


def affine(rotation, translation):
    return (rotation, translation)


def compose(outer, inner):
    rotation = matmul(outer[0], inner[0])
    moved = apply(outer[0], inner[1])
    return (rotation, tuple(outer[1][i] + moved[i] for i in range(3)))


def transform(frame, point):
    moved = apply(frame[0], point)
    return tuple(frame[1][i] + moved[i] for i in range(3))


IDENTITY = ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], (0.0, 0.0, 0.0))


def vanilla_quads(cube, width, height):
    """The cube's quads as ModelPart.Cube builds them: positions and UVs in part space."""
    x0, y0, z0 = cube["origin"]
    sx, sy, sz = cube["size"]
    gx, gy, gz = cube["grow"]
    u, v = cube["uv"]
    mirror = cube["mirror"]
    x1, y1, z1 = x0 + sx + gx, y0 + sy + gy, z0 + sz + gz
    x0, y0, z0 = x0 - gx, y0 - gy, z0 - gz
    if mirror:
        x0, x1 = x1, x0
    p1, p2, p3, p4 = (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)
    p5, p6, p7, p8 = (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)
    w, x, y = u, u + sz, u + sz + sx
    z, aa, ab = u + sz + sx + sx, u + sz + sx + sz, u + sz + sx + sz + sx
    ac, ad, ae = v, v + sz, v + sz + sy
    faces = [
        ([p6, p5, p1, p2], x, ac, y, ad),
        ([p3, p4, p8, p7], y, ad, z, ac),
        ([p1, p5, p8, p4], w, ad, x, ae),
        ([p2, p1, p4, p3], x, ad, y, ae),
        ([p6, p2, p3, p7], y, ad, aa, ae),
        ([p5, p6, p7, p8], aa, ad, ab, ae),
    ]
    quads = []
    for points, fu0, fv0, fu1, fv1 in faces:
        uvs = [(fu1 / width, fv0 / height), (fu0 / width, fv0 / height),
               (fu0 / width, fv1 / height), (fu1 / width, fv1 / height)]
        vertices = list(zip(points, uvs))
        if mirror:
            vertices.reverse()
        quads.append(vertices)
    return quads


def geo_quads(cube, width, height):
    """The geo cube's quads as GeoModel bakes them: positions in baked space, and UVs."""
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    inflate = cube.get("inflate", 0.0)
    mirror = cube.get("mirror", False)
    x0, y0, z0 = -(ox + sx) - inflate, oy - inflate, oz - inflate
    x1, y1, z1 = -ox + inflate, oy + sy + inflate, oz + sz + inflate
    blb, brb = (x0, y0, z0), (x0, y0, z1)
    tlb, trb = (x0, y1, z0), (x0, y1, z1)
    tlf, trf = (x1, y1, z0), (x1, y1, z1)
    blf, brf = (x1, y0, z0), (x1, y0, z1)
    box = isinstance(cube["uv"], list)
    corners = {
        "west": [trb, tlb, blb, brb],
        "east": [tlf, trf, brf, blf],
        "north": [tlb, tlf, blf, blb],
        "south": [trf, trb, brb, brf],
        "up": [trb, trf, tlf, tlb],
        "down": [blb, blf, brf, brb],
    }
    swap = {"west": "east", "east": "west"}
    if not box:
        swap.update({"up": "down", "down": "up"})
    quads = []
    for face in ("west", "east", "north", "south", "up", "down"):
        rect = face_uv(cube["uv"], face, cube["size"])
        if rect is None:
            continue
        side = swap.get(face, face) if mirror else face
        fu, fv, fw, fh = rect
        u0, v0, u1, v1 = fu / width, fv / height, (fu + fw) / width, (fv + fh) / height
        if not mirror:
            u0, u1 = u1, u0
        uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        quads.append(list(zip(corners[side], uvs)))
    return quads


def face_uv(uv, face, size):
    if not isinstance(uv, list):
        entry = uv.get(face)
        return None if entry is None else (*entry["uv"], *entry["uv_size"])
    u, v = uv
    x, y, z = (math.floor(value) for value in size)
    return {
        "west": (u + z + x, v + z, z, y),
        "east": (u, v + z, z, y),
        "north": (u + z, v + z, x, y),
        "south": (u + z + x + z, v + z, x, y),
        "up": (u + z, v, x, z),
        "down": (u + z + x, v + z, x, -z),
    }[face]


def per_face_uv(cube):
    """Vanilla's box layout written out per face, exact for fractional sizes."""
    sx, sy, sz = cube["size"]
    u, v = cube["uv"]
    return {
        "east": {"uv": [u, v + sz], "uv_size": [sz, sy]},
        "north": {"uv": [u + sz, v + sz], "uv_size": [sx, sy]},
        "west": {"uv": [u + sz + sx, v + sz], "uv_size": [sz, sy]},
        "south": {"uv": [u + sz + sx + sz, v + sz], "uv_size": [sx, sy]},
        "up": {"uv": [u + sz, v], "uv_size": [sx, sz]},
        "down": {"uv": [u + sz + sx, v + sz], "uv_size": [sx, -sz]},
    }


def geo_cube(cube, absolute):
    """A Java cube at an absolute rest position, as a Bedrock cube."""
    ax, ay, az = absolute
    x0, y0, z0 = cube["origin"]
    sx, sy, sz = cube["size"]
    gx, gy, gz = cube["grow"]
    uniform = gx == gy == gz
    integral = all(float(value).is_integer() for value in cube["size"])
    out = {}
    if uniform:
        out["origin"] = [ax + x0, 24.0 - (ay + y0) - sy, az + z0]
        out["size"] = [sx, sy, sz]
        if gx:
            out["inflate"] = gx
    else:
        out["origin"] = [ax + x0 - gx, 24.0 - (ay + y0) - sy - gy, az + z0 - gz]
        out["size"] = [sx + 2 * gx, sy + 2 * gy, sz + 2 * gz]
    out["uv"] = list(cube["uv"]) if uniform and integral else per_face_uv(cube)
    if cube["mirror"]:
        out["mirror"] = True
    return out


def java_space(point):
    return (point[0], 24.0 - point[1], point[2])


def geo_bone_frames(bones):
    """Each bone's rest matrix as GeoModel builds it, with armour bones posed at vanilla rest."""
    frames = {}
    for bone in bones:
        name = bone["name"]
        pivot = bone["pivot"]
        rest = bone.get("rotation", [0.0, 0.0, 0.0])
        offset = (0.0, 0.0, 0.0)
        if "root" in bone:
            root = bone["root"]
            part = REST_POSE[root]
            dx, dy = SEGMENT_OFFSETS[root]
            offset = (part[0] + dx, dy - part[1], part[2])
        p = (-pivot[0], pivot[1], pivot[2])
        rotation = rotation_zyx(math.radians(-rest[0]), math.radians(-rest[1]), math.radians(rest[2]))
        local = affine(rotation, (0.0, 0.0, 0.0))
        local = compose(affine(IDENTITY[0], (-offset[0] + p[0], offset[1] + p[1], offset[2] + p[2])), local)
        local = compose(local, affine(IDENTITY[0], (-p[0], -p[1], -p[2])))
        parent = bone.get("parent")
        frames[name] = local if parent is None else compose(frames[parent], local)
    return frames


def close(a, b):
    return all(abs(x - y) < 1e-3 for x, y in zip(a, b))


def verify(expected, bones, width, height, label):
    """Fails unless every geo quad lands exactly where the Java model draws it."""
    frames = geo_bone_frames(bones)
    actual = []
    for bone in bones:
        for cube in bone.get("cubes", []):
            for quad in geo_quads(cube, width, height):
                actual.append([(java_space(flip(transform(frames[bone["name"]], p))), uv) for p, uv in quad])
    if len(actual) != len(expected):
        raise PortError(f"{label}: {len(expected)} quads in Java, {len(actual)} in geo")
    remaining = list(actual)
    for quad in expected:
        match = next((other for other in remaining if same_quad(quad, other)), None)
        if match is None:
            raise PortError(f"{label}: no geo quad matches Java quad {quad}")
        remaining.remove(match)


def flip(point):
    return (-point[0], point[1], point[2])


def same_quad(a, b):
    """Equal as drawn: same corners with the same UVs, in the same winding."""
    for shift in range(4):
        if all(close(a[i][0], b[(i + shift) % 4][0]) and close(a[i][1], b[(i + shift) % 4][1])
               for i in range(4)):
            return True
    return False


class SetBuilder:
    """Merges the per-slot Java meshes of one set into one geo."""

    def __init__(self, label):
        self.label = label
        self.bones = []
        self.names = {}
        self.expected = []
        self.size = None
        self.rest = {}

    def add_slot(self, slot, layer):
        mesh, width, height = layer
        if self.size not in (None, (width, height)):
            raise PortError(f"{self.label}: {slot} texture is {width}x{height}, not {self.size}")
        self.size = (width, height)
        for root, part in mesh.root.children.items():
            if not has_cubes(part):
                continue
            key = (slot, root)
            if key not in BONES:
                raise PortError(f"{self.label}: {slot} model draws {root}, which no armour bone follows")
            bone_name = BONES[key]
            anchor = ANCHORS[root]
            frame = affine(IDENTITY[0], REST_POSE[root])
            existing = next((bone for bone in self.bones if bone["name"] == bone_name), None)
            if existing is None:
                existing = {
                    "name": bone_name,
                    "pivot": list(java_space(anchor)),
                    "root": root,
                    "cubes": [],
                }
                self.bones.append(existing)
            self.add_cubes(existing, part, anchor, frame, width, height)
            for child_name, child in part.children.items():
                self.add_part(slot, bone_name, child_name, child, anchor, frame, width, height)

    def add_part(self, slot, parent, name, part, parent_pivot, parent_frame, width, height):
        x, y, z, rx, ry, rz = part.pose
        pivot = (parent_pivot[0] + x, parent_pivot[1] + y, parent_pivot[2] + z)
        frame = compose(parent_frame, affine(rotation_zyx(rx, ry, rz), (x, y, z)))
        bone_name = name if name not in self.names.values() else f"{name}_{slot}"
        self.names[(slot, name)] = bone_name
        self.rest[(slot, name)] = part.pose
        bone = {"name": bone_name, "parent": parent, "pivot": list(java_space(pivot)), "cubes": []}
        if rx or ry or rz:
            bone["rotation"] = [math.degrees(rx), math.degrees(ry), math.degrees(rz)]
        self.bones.append(bone)
        self.add_cubes(bone, part, pivot, frame, width, height)
        for child_name, child in part.children.items():
            self.add_part(slot, bone_name, child_name, child, pivot, frame, width, height)

    def add_cubes(self, bone, part, pivot, frame, width, height):
        for cube in part.cubes:
            bone["cubes"].append(geo_cube(cube, pivot))
            for quad in vanilla_quads(cube, width, height):
                self.expected.append([(transform(frame, p), uv) for p, uv in quad])

    def geo(self, identifier):
        verify(self.expected, self.bones, self.size[0], self.size[1], self.label)
        bones = []
        for bone in self.bones:
            out = {key: rounded(value) for key, value in bone.items() if key != "root" and value != []}
            bones.append(out)
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": identifier,
                    "texture_width": self.size[0],
                    "texture_height": self.size[1],
                },
                "bones": bones,
            }],
        }


def rounded(value):
    if isinstance(value, float):
        result = round(value, 4)
        return int(result) if result.is_integer() else result
    if isinstance(value, list):
        return [rounded(item) for item in value]
    if isinstance(value, dict):
        return {key: rounded(item) for key, item in value.items()}
    return value


def has_cubes(part):
    return bool(part.cubes) or any(has_cubes(child) for child in part.children.values())


PART_QUERIES = ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")


FUNCTIONS = {
    "sinpi": "math.sin(180 * ({}))",
    "cospi": "math.cos(180 * ({}))",
    "abs": "math.abs({})",
    "min": "math.min({})",
    "max": "math.max({})",
    "mod": "math.mod({})",
}

NAMES = {
    "age": "(q.life_time * 20)",
    "pi": "3.14159265",
    "head_yaw": "q.head_y_rotation",
    "head_pitch": "q.head_x_rotation",
    "riding": "q.is_riding",
    "crouching": "q.is_sneaking",
}


def molang(expression):
    """Rewrites a manifest expression, in Java units, into Molang."""
    out = []
    index = 0
    while index < len(expression):
        match = re.compile(r"[A-Za-z_][\w.]*").match(expression, index)
        if match is None:
            out.append(expression[index])
            index += 1
            continue
        name = match.group()
        index = match.end()
        if name in FUNCTIONS and expression[index:index + 1] == "(":
            end = closing_paren(expression, index)
            out.append(FUNCTIONS[name].format(molang(expression[index + 1:end])))
            index = end + 1
        elif name in NAMES:
            out.append(NAMES[name])
        elif re.fullmatch(r"(\w+)\.([xyz])_rot", name) and name.split(".")[0] in PART_QUERIES:
            part, axis = name.split(".")
            out.append(f"(q.{part}_{axis[0]}_rotation / 57.29578)")
        else:
            raise PortError(f"unknown name {name!r} in animation expression {expression!r}")
    return "".join(out)


def closing_paren(text, start):
    depth = 0
    for index in range(start, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return index
    raise PortError(f"unbalanced parentheses in {text!r}")


def channel(values, rest, keys, convert):
    if not any(key in values for key in keys):
        return None
    out = []
    for index, key in enumerate(keys):
        if key not in values:
            out.append(0)
        else:
            out.append(convert(index, f"({molang(values[key])})", rest[index]))
    return out


def rotation_track(index, expression, rest):
    return f"{expression} * 57.29578 - {rounded(math.degrees(rest))}"


def position_track(index, expression, rest):
    delta = f"{expression} - {rounded(rest)}"
    return f"-({delta})" if index == 1 else delta


def moved_rest(values, rest, slim_rest):
    """Whether the slim model rests differently on a channel the animation drives."""
    keys = ("x", "y", "z", "x_rot", "y_rot", "z_rot")
    return slim_rest is not None and any(key in values and abs(rest[index] - slim_rest[index]) > 1e-6
                                         for index, key in enumerate(keys))


def animation(builder, animations, slim):
    bones = {}
    for slot, parts in animations.items():
        for part, values in parts.items():
            key = (slot, part)
            if key not in builder.names:
                raise PortError(f"{builder.label}: animation names {slot} part {part}, which the model does not define")
            rest = (0.0,) * 6 if values.get("relative") else builder.rest[key]
            if slim is not None and not values.get("relative") and moved_rest(values, rest, slim.rest.get(key)):
                print(f"warning: {builder.label}: {slot} {part} rests differently when slim;"
                      " its animation follows the default model", file=sys.stderr)
            track = {}
            rotation = channel(values, rest[3:], ("x_rot", "y_rot", "z_rot"), rotation_track)
            position = channel(values, rest[:3], ("x", "y", "z"), position_track)
            if rotation:
                track["rotation"] = rotation
            if position:
                track["position"] = position
            bones[builder.names[key]] = track
    return {
        "format_version": "1.8.0",
        "animations": {"idle": {"loop": True, "bones": bones}},
    }


def write(path, document):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="ascii") as handle:
        json.dump(document, handle, indent=2)
        handle.write("\n")


def port_set(evaluator, namespace, name, spec, sources_dir, out_dir):
    slots = spec["slots"]
    layer_method = spec.get("layer_method", "createLayerDefinition")
    slim_method = spec.get("slim_method")
    evaluators = {}
    for slot, path in slots.items():
        if slot not in SLOTS:
            raise PortError(f"{name}: unknown slot {slot}")
        source = Source(os.path.join(sources_dir, path))
        evaluators[slot] = (source, Evaluator([source] + evaluator.sources))

    default = SetBuilder(name)
    slim = SetBuilder(f"{name} (slim)") if slim_method else None
    has_slim = False
    for slot, (source, slot_evaluator) in evaluators.items():
        default.add_slot(slot, slot_evaluator.call(layer_method, []))
        if slim is not None:
            method = slim_method if slim_method in source.methods else layer_method
            has_slim = has_slim or method == slim_method
            slim.add_slot(slot, slot_evaluator.call(method, []))

    geo_id = f"{namespace}:geo/armor/{name}.geo.json"
    write(os.path.join(out_dir, namespace, "geo", "armor", f"{name}.geo.json"),
          default.geo(f"geometry.{namespace}.{name}"))
    slim_geo_id = None
    if has_slim:
        slim_geo_id = f"{namespace}:geo/armor/{name}_slim.geo.json"
        write(os.path.join(out_dir, namespace, "geo", "armor", f"{name}_slim.geo.json"),
              slim.geo(f"geometry.{namespace}.{name}_slim"))

    animations_id = None
    if spec.get("animations"):
        animations_id = f"{namespace}:animations/armor/{name}.animation.json"
        write(os.path.join(out_dir, namespace, "animations", "armor", f"{name}.animation.json"),
              animation(default, spec["animations"], slim if has_slim else None))

    for skin in spec.get("skins", [""]):
        definition = {"geo": geo_id}
        if slim_geo_id:
            definition["slim_geo"] = slim_geo_id
        definition["texture"] = spec["texture"].format(skin=skin)
        if "slim_texture" in spec:
            definition["slim_texture"] = spec["slim_texture"].format(skin=skin)
        if animations_id:
            definition["animations"] = animations_id
            definition["idle"] = "idle"
            definition["transition_ticks"] = 0
        write(os.path.join(out_dir, namespace, "geo_armor", f"{skin}{name}.json"), definition)
    print(f"{name}: {len(default.bones)} bones, {len(default.expected)} quads verified"
          + (", slim verified" if has_slim else ""))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("manifest")
    parser.add_argument("--sources", required=True, help="root of the decompiled Java sources")
    parser.add_argument("--out", required=True, help="assets directory to write into")
    args = parser.parse_args()

    with open(args.manifest, encoding="utf-8") as handle:
        manifest = json.load(handle)
    namespace = manifest["namespace"]
    helpers = [Source(os.path.join(args.sources, path)) for path in manifest.get("helpers", [])]
    evaluator = Evaluator(helpers)
    try:
        for name, spec in manifest["sets"].items():
            port_set(evaluator, namespace, name, spec, args.sources, args.out)
    except PortError as error:
        sys.exit(f"error: {error}")


if __name__ == "__main__":
    main()
