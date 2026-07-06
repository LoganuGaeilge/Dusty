package com.yourserver.customenchants.util;

/**
 * A tiny math expression evaluator used to let ANY numeric config field
 * (success-chance, upgrade-costs, values:, ranges: min/max, and inline
 * chance:/setvar:/randomvar:/blockdrop: amounts) accept a real formula
 * instead of just a flat number or a {base, per-level} table.
 *
 * The only variable exposed is "level" (the enchant's current level - the
 * book's level when resolving success-chance, the item's applied level
 * everywhere else). Everything else is plain arithmetic.
 *
 * Supported syntax:
 *   Numbers:      1, 2.5, -3
 *   Variable:     level
 *   Operators:    +  -  *  /  %  ^ (power)  and parentheses
 *   Functions:    min(a, b)  max(a, b)  floor(x)  ceil(x)  round(x)  abs(x)  sqrt(x)
 *
 * Examples:
 *   "level * 150 + 50"                  -> linear cost that grows with level
 *   "100 * (1.15 ^ (level - 1))"        -> compounding/exponential cost curve
 *   "min(level, 6)"                     -> cap a value at 6 without a hard clamp field
 *   "max(1, level - 2)"                 -> floor a value at 1
 *   "level"                             -> literal shorthand for "equal to current level"
 *
 * Parsing/evaluation never throws outward: {@link #tryEvaluate(String, int)}
 * returns null on anything it can't confidently parse, so callers can fall
 * back to their previous behaviour (flat number, range literal, etc.)
 * without any risk of this new capability breaking an existing config.
 */
public final class MathExpr {

    private MathExpr() {
    }

    /** Evaluates {@code expression} with "level" bound to {@code level}, or returns null if it isn't a valid expression. */
    public static Double tryEvaluate(String expression, int level) {
        if (expression == null) return null;
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) return null;
        try {
            Parser parser = new Parser(trimmed, level);
            double result = parser.parseExpression();
            parser.skipWhitespace();
            if (!parser.atEnd()) {
                return null; // leftover input - not a clean expression, let the caller fall back
            }
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return null;
            }
            return result;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Returns true if the string contains anything suggesting it's meant as a formula (letters, or math-only symbols), used to decide whether to attempt parsing at all. */
    public static boolean looksLikeExpression(String s) {
        if (s == null) return false;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetter(c) || c == '(' || c == ')' || c == '*' || c == '/' || c == '^' || c == '%') {
                return true;
            }
        }
        return false;
    }

    private static final class Parser {
        private final String src;
        private final int level;
        private int pos;

        Parser(String src, int level) {
            this.src = src;
            this.level = level;
            this.pos = 0;
        }

        boolean atEnd() {
            return pos >= src.length();
        }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (peek('+')) {
                    pos++;
                    value += parseTerm();
                } else if (peek('-')) {
                    pos++;
                    value -= parseTerm();
                } else {
                    break;
                }
            }
            return value;
        }

        double parseTerm() {
            double value = parsePower();
            while (true) {
                skipWhitespace();
                if (peek('*')) {
                    pos++;
                    value *= parsePower();
                } else if (peek('/')) {
                    pos++;
                    double divisor = parsePower();
                    value /= divisor;
                } else if (peek('%')) {
                    pos++;
                    value %= parsePower();
                } else {
                    break;
                }
            }
            return value;
        }

        double parsePower() {
            double base = parseUnary();
            skipWhitespace();
            if (peek('^')) {
                pos++;
                double exponent = parsePower(); // right-associative
                return Math.pow(base, exponent);
            }
            return base;
        }

        double parseUnary() {
            skipWhitespace();
            if (peek('-')) {
                pos++;
                return -parseUnary();
            }
            if (peek('+')) {
                pos++;
                return parseUnary();
            }
            return parsePrimary();
        }

        double parsePrimary() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalStateException("Unexpected end of expression");
            }

            char c = src.charAt(pos);

            if (c == '(') {
                pos++;
                double value = parseExpression();
                skipWhitespace();
                expect(')');
                return value;
            }

            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }

            if (Character.isLetter(c) || c == '_') {
                String ident = parseIdentifier();
                skipWhitespace();
                if (peek('(')) {
                    return parseFunctionCall(ident);
                }
                if (ident.equalsIgnoreCase("level")) {
                    return level;
                }
                throw new IllegalStateException("Unknown identifier: " + ident);
            }

            throw new IllegalStateException("Unexpected character '" + c + "' in expression");
        }

        double parseFunctionCall(String name) {
            expect('(');
            java.util.List<Double> args = new java.util.ArrayList<>();
            skipWhitespace();
            if (!peek(')')) {
                args.add(parseExpression());
                skipWhitespace();
                while (peek(',')) {
                    pos++;
                    args.add(parseExpression());
                    skipWhitespace();
                }
            }
            expect(')');

            switch (name.toLowerCase()) {
                case "min":
                    require(args, 2, name);
                    return Math.min(args.get(0), args.get(1));
                case "max":
                    require(args, 2, name);
                    return Math.max(args.get(0), args.get(1));
                case "floor":
                    require(args, 1, name);
                    return Math.floor(args.get(0));
                case "ceil":
                    require(args, 1, name);
                    return Math.ceil(args.get(0));
                case "round":
                    require(args, 1, name);
                    return (double) Math.round(args.get(0));
                case "abs":
                    require(args, 1, name);
                    return Math.abs(args.get(0));
                case "sqrt":
                    require(args, 1, name);
                    return Math.sqrt(args.get(0));
                default:
                    throw new IllegalStateException("Unknown function: " + name);
            }
        }

        void require(java.util.List<Double> args, int count, String name) {
            if (args.size() != count) {
                throw new IllegalStateException(name + "() expects " + count + " argument(s)");
            }
        }

        double parseNumber() {
            int start = pos;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
                pos++;
            }
            String numStr = src.substring(start, pos);
            return Double.parseDouble(numStr);
        }

        String parseIdentifier() {
            int start = pos;
            while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
                pos++;
            }
            return src.substring(start, pos);
        }

        boolean peek(char c) {
            return pos < src.length() && src.charAt(pos) == c;
        }

        void expect(char c) {
            if (!peek(c)) {
                throw new IllegalStateException("Expected '" + c + "'");
            }
            pos++;
        }
    }
}
