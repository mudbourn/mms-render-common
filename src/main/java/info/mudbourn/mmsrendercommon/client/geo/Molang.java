package info.mudbourn.mmsrendercommon.client.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A compiler for the single-expression subset of Molang used in animation keyframes.
 *
 * <p>Supported: numbers, parentheses, unary {@code -} and {@code !}, the arithmetic,
 * comparison, logical and ternary operators, {@code query.}/{@code q.} and
 * {@code variable.}/{@code v.} lookups, {@code math.pi}, and the {@code math.} functions
 * {@code sin cos asin acos atan atan2 abs ceil floor round trunc clamp lerp min max mod pow
 * sqrt exp ln}. Trigonometry is in degrees, as in Bedrock. Anything else, including
 * multi-statement Molang, fails to compile with an {@link IllegalArgumentException}.
 */
public final class Molang {

    /** A compiled expression. */
    @FunctionalInterface
    public interface Expr {

        /** Evaluates against a scope, with {@code query.anim_time} read as the given seconds. */
        double eval(MolangScope scope, double animTime);
    }

    /** An expression with no lookups, folded to its value at compile time. */
    public record Constant(double value) implements Expr {

        @Override
        public double eval(MolangScope scope, double animTime) {
            return this.value;
        }
    }

    private final String source;
    private int index;

    private Molang(String source) {
        this.source = source.toLowerCase(Locale.ROOT);
    }

    /** Compiles one expression. */
    public static Expr compile(String source) {
        Molang parser = new Molang(source);
        Expr expr = parser.ternary();
        parser.skipSpace();
        if (parser.index < parser.source.length()) {
            throw parser.error("unexpected '" + parser.source.charAt(parser.index) + "'");
        }
        return expr;
    }

    private Expr ternary() {
        Expr condition = logicalOr();
        if (!accept("?")) {
            return condition;
        }
        Expr then = ternary();
        expect(":");
        Expr otherwise = ternary();
        Expr result = (scope, t) -> condition.eval(scope, t) != 0.0
                ? then.eval(scope, t)
                : otherwise.eval(scope, t);
        return fold(condition, then, otherwise, result);
    }

    private Expr logicalOr() {
        Expr left = logicalAnd();
        while (accept("||")) {
            Expr a = left;
            Expr b = logicalAnd();
            left = fold(a, b, (scope, t) -> truth(a.eval(scope, t) != 0.0 || b.eval(scope, t) != 0.0));
        }
        return left;
    }

    private Expr logicalAnd() {
        Expr left = comparison();
        while (accept("&&")) {
            Expr a = left;
            Expr b = comparison();
            left = fold(a, b, (scope, t) -> truth(a.eval(scope, t) != 0.0 && b.eval(scope, t) != 0.0));
        }
        return left;
    }

    private Expr comparison() {
        Expr left = additive();
        while (true) {
            Expr a = left;
            if (accept("==")) {
                Expr b = additive();
                left = fold(a, b, (scope, t) -> truth(a.eval(scope, t) == b.eval(scope, t)));
            } else if (accept("!=")) {
                Expr b = additive();
                left = fold(a, b, (scope, t) -> truth(a.eval(scope, t) != b.eval(scope, t)));
            } else if (accept("<=")) {
                Expr b = additive();
                left = fold(a, b, (scope, t) -> truth(a.eval(scope, t) <= b.eval(scope, t)));
            } else if (accept(">=")) {
                Expr b = additive();
                left = fold(a, b, (scope, t) -> truth(a.eval(scope, t) >= b.eval(scope, t)));
            } else if (accept("<")) {
                Expr b = additive();
                left = fold(a, b, (scope, t) -> truth(a.eval(scope, t) < b.eval(scope, t)));
            } else if (accept(">")) {
                Expr b = additive();
                left = fold(a, b, (scope, t) -> truth(a.eval(scope, t) > b.eval(scope, t)));
            } else {
                return left;
            }
        }
    }

    private Expr additive() {
        Expr left = multiplicative();
        while (true) {
            Expr a = left;
            if (accept("+")) {
                Expr b = multiplicative();
                left = fold(a, b, (scope, t) -> a.eval(scope, t) + b.eval(scope, t));
            } else if (accept("-")) {
                Expr b = multiplicative();
                left = fold(a, b, (scope, t) -> a.eval(scope, t) - b.eval(scope, t));
            } else {
                return left;
            }
        }
    }

    private Expr multiplicative() {
        Expr left = unary();
        while (true) {
            Expr a = left;
            if (accept("*")) {
                Expr b = unary();
                left = fold(a, b, (scope, t) -> a.eval(scope, t) * b.eval(scope, t));
            } else if (accept("/")) {
                Expr b = unary();
                left = fold(a, b, (scope, t) -> a.eval(scope, t) / b.eval(scope, t));
            } else {
                return left;
            }
        }
    }

    private Expr unary() {
        if (accept("-")) {
            Expr operand = unary();
            return fold(operand, (scope, t) -> -operand.eval(scope, t));
        }
        if (accept("!")) {
            Expr operand = unary();
            return fold(operand, (scope, t) -> truth(operand.eval(scope, t) == 0.0));
        }
        return primary();
    }

    private Expr primary() {
        skipSpace();
        if (accept("(")) {
            Expr inner = ternary();
            expect(")");
            return inner;
        }
        if (this.index < this.source.length()) {
            char c = this.source.charAt(this.index);
            if (Character.isDigit(c) || c == '.') {
                return number();
            }
            if (Character.isLetter(c) || c == '_') {
                return identifier();
            }
        }
        throw error("expected a value");
    }

    private Expr number() {
        int start = this.index;
        while (this.index < this.source.length()) {
            char c = this.source.charAt(this.index);
            if (!Character.isDigit(c) && c != '.') {
                break;
            }
            this.index++;
        }
        double value = Double.parseDouble(this.source.substring(start, this.index));
        if (this.index < this.source.length() && this.source.charAt(this.index) == 'f') {
            this.index++;
        }
        return new Constant(value);
    }

    private Expr identifier() {
        int start = this.index;
        while (this.index < this.source.length()) {
            char c = this.source.charAt(this.index);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.') {
                break;
            }
            this.index++;
        }
        String name = this.source.substring(start, this.index);
        int dot = name.indexOf('.');
        String head = dot < 0 ? name : name.substring(0, dot);
        String tail = dot < 0 ? "" : name.substring(dot + 1);

        switch (head) {
            case "query", "q" -> {
                if (tail.equals("anim_time")) {
                    return (scope, t) -> t;
                }
                return (scope, t) -> scope.query(tail);
            }
            case "variable", "v" -> {
                return (scope, t) -> scope.variable(tail);
            }
            case "math" -> {
                if (tail.equals("pi")) {
                    return new Constant(Math.PI);
                }
                return function(tail, arguments());
            }
            case "this" -> {
                return new Constant(0.0);
            }
            default -> throw error("unknown name '" + name + "'");
        }
    }

    private List<Expr> arguments() {
        List<Expr> arguments = new ArrayList<>();
        expect("(");
        if (accept(")")) {
            return arguments;
        }
        do {
            arguments.add(ternary());
        } while (accept(","));
        expect(")");
        return arguments;
    }

    private Expr function(String name, List<Expr> args) {
        return switch (name) {
            case "sin" -> unaryFunction(name, args, x -> Math.sin(Math.toRadians(x)));
            case "cos" -> unaryFunction(name, args, x -> Math.cos(Math.toRadians(x)));
            case "asin" -> unaryFunction(name, args, x -> Math.toDegrees(Math.asin(x)));
            case "acos" -> unaryFunction(name, args, x -> Math.toDegrees(Math.acos(x)));
            case "atan" -> unaryFunction(name, args, x -> Math.toDegrees(Math.atan(x)));
            case "abs" -> unaryFunction(name, args, Math::abs);
            case "ceil" -> unaryFunction(name, args, Math::ceil);
            case "floor" -> unaryFunction(name, args, Math::floor);
            case "round" -> unaryFunction(name, args, x -> (double) Math.round(x));
            case "trunc" -> unaryFunction(name, args, x -> x < 0.0 ? Math.ceil(x) : Math.floor(x));
            case "sqrt" -> unaryFunction(name, args, Math::sqrt);
            case "exp" -> unaryFunction(name, args, Math::exp);
            case "ln" -> unaryFunction(name, args, Math::log);
            case "atan2" -> binaryFunction(name, args, (y, x) -> Math.toDegrees(Math.atan2(y, x)));
            case "min" -> binaryFunction(name, args, Math::min);
            case "max" -> binaryFunction(name, args, Math::max);
            case "mod" -> binaryFunction(name, args, (a, b) -> a % b);
            case "pow" -> binaryFunction(name, args, Math::pow);
            case "clamp" -> ternaryFunction(name, args, (v, lo, hi) -> Math.max(lo, Math.min(hi, v)));
            case "lerp" -> ternaryFunction(name, args, (a, b, t) -> a + (b - a) * t);
            default -> throw error("unknown function 'math." + name + "'");
        };
    }

    @FunctionalInterface
    private interface Op1 {
        double apply(double a);
    }

    @FunctionalInterface
    private interface Op2 {
        double apply(double a, double b);
    }

    @FunctionalInterface
    private interface Op3 {
        double apply(double a, double b, double c);
    }

    private Expr unaryFunction(String name, List<Expr> args, Op1 op) {
        arity(name, args, 1);
        Expr a = args.get(0);
        return fold(a, (scope, t) -> op.apply(a.eval(scope, t)));
    }

    private Expr binaryFunction(String name, List<Expr> args, Op2 op) {
        arity(name, args, 2);
        Expr a = args.get(0);
        Expr b = args.get(1);
        return fold(a, b, (scope, t) -> op.apply(a.eval(scope, t), b.eval(scope, t)));
    }

    private Expr ternaryFunction(String name, List<Expr> args, Op3 op) {
        arity(name, args, 3);
        Expr a = args.get(0);
        Expr b = args.get(1);
        Expr c = args.get(2);
        return fold(a, b, c, (scope, t) -> op.apply(a.eval(scope, t), b.eval(scope, t), c.eval(scope, t)));
    }

    private void arity(String name, List<Expr> args, int count) {
        if (args.size() != count) {
            throw error("math." + name + " takes " + count + " arguments, got " + args.size());
        }
    }

    /** Collapses an expression to a {@link Constant} when every operand is one. */
    private static Expr fold(Expr a, Expr result) {
        return a instanceof Constant ? new Constant(result.eval(null, 0.0)) : result;
    }

    private static Expr fold(Expr a, Expr b, Expr result) {
        return a instanceof Constant && b instanceof Constant ? new Constant(result.eval(null, 0.0)) : result;
    }

    private static Expr fold(Expr a, Expr b, Expr c, Expr result) {
        boolean constant = a instanceof Constant && b instanceof Constant && c instanceof Constant;
        return constant ? new Constant(result.eval(null, 0.0)) : result;
    }

    private static double truth(boolean value) {
        return value ? 1.0 : 0.0;
    }

    private boolean accept(String token) {
        skipSpace();
        if (this.source.startsWith(token, this.index)) {
            this.index += token.length();
            return true;
        }
        return false;
    }

    private void expect(String token) {
        if (!accept(token)) {
            throw error("expected '" + token + "'");
        }
    }

    private void skipSpace() {
        while (this.index < this.source.length() && Character.isWhitespace(this.source.charAt(this.index))) {
            this.index++;
        }
    }

    private IllegalArgumentException error(String message) {
        String where = " at " + this.index + " in \"" + this.source + "\"";
        return new IllegalArgumentException("Molang: " + message + where);
    }
}
