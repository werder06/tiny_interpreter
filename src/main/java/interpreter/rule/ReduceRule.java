package interpreter.rule;


import interpreter.Context;
import interpreter.expression.Expression;
import interpreter.expression.NumberExpression;
import interpreter.expression.ReduceExpression;
import interpreter.expression.SequenceExpression;
import interpreter.parser.Parser;
import interpreter.error.ParserError;
import interpreter.parser.TwoArgsLambda;

import static interpreter.Util.emptyNumberExpression;


/**
 * Rule for 'reduce' function recognition.
 * <p/>
 * Signature: reduce(sequence, expr, identifier identifier -> expr)
 */
public class ReduceRule implements Rule {

    private static final String START = "reduce(";
    private static final String END = ")";

    @Override
    public boolean couldBeApplied(String line) {
        return line.startsWith(START) && line.endsWith(END);
    }

    @Override
    public Expression parse(Parser parser, String line, Context context) {
        String args = line.substring(START.length(), line.length() - 1);
        int firstPosition = 0;
        Context localContext = new Context(context);
        while ((firstPosition = args.indexOf(",", firstPosition + 1)) != -1) {
            localContext.clearExceptions();
            String newLine = args.substring(0, firstPosition);
            SequenceExpression sequence = parser.sequenceExpression(localContext, newLine);
            if (!localContext.hasException()) {
                int secondPosition = firstPosition;
                while ((secondPosition = args.indexOf(",", secondPosition + 1)) != -1) {
                    localContext.clearExceptions();
                    newLine = args.substring(firstPosition + 1, secondPosition);
                    NumberExpression expression = parser.numberExpression(localContext, newLine);
                    if (!localContext.hasException()) {
                        newLine = args.substring(secondPosition + 1, args.length());
                        TwoArgsLambda lambda = parser.parseTwoArgsLambda(localContext, newLine);
                        if (!localContext.hasException()) {
                            return new ReduceExpression(sequence, expression,
                                    lambda.getFirstIdentifier(), lambda.getSecondIdentifier(), lambda.getException());
                        } else {
                            firstPosition = args.length();
                            secondPosition = args.length();
                        }
                    }
                }
            } else if (localContext.hasIncompatibleParseError()) {
                firstPosition = args.length();
            }
        }
        if (localContext.hasException()) {
            context.addExceptions(localContext.getExceptions());
        } else {
            context.addException(new ParserError("Invalid syntax for reduce function '" + line + "'"));
        }
        return emptyNumberExpression();
    }
}
