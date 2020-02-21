package io.cucumber.cucumberexpressions;

import static io.cucumber.cucumberexpressions.ParameterType.createAnonymousParameterType;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class RegularExpression implements Expression {
    private final Pattern expressionRegexp;
    private final ParameterTypeRegistry parameterTypeRegistry;
    private final TreeRegexp treeRegexp;

    /**
     * Creates a new instance. Use this when the transform types are not known in advance,
     * and should be determined by the regular expression's capture groups. Use this with
     * dynamically typed languages.
     *
     * @param expressionRegexp      the regular expression to use
     * @param parameterTypeRegistry used to look up parameter types
     */
    RegularExpression(Pattern expressionRegexp, ParameterTypeRegistry parameterTypeRegistry) {
        this.expressionRegexp = expressionRegexp;
        this.parameterTypeRegistry = parameterTypeRegistry;
        this.treeRegexp = new TreeRegexp(expressionRegexp);
    }

    @Override
    public List<Argument<?>> match(String text, Type... typeHints) {
        final Group group = treeRegexp.match(text);
        if (group == null) {
            return null;
        }

        final ParameterByTypeTransformer defaultTransformer = parameterTypeRegistry.getDefaultParameterTransformer();
        final List<ParameterType<?>> parameterTypes = new ArrayList<>();
        int typeHintIndex = 0;
        for (GroupBuilder groupBuilder : treeRegexp.getGroupBuilder().getChildren()) {
            final String parameterTypeRegexp = groupBuilder.getSource();
            boolean hasTypeHint = typeHintIndex < typeHints.length;
            final Type typeHint = hasTypeHint ? typeHints[typeHintIndex++] : String.class;

            ParameterType<?> parameterType;
            if (hasTypeHint) {
                // When there is a type hint, use it. For strongly typed languages, the target
                // type must match the type of the step definition.
                parameterType = createAnonymousParameterType(parameterTypeRegexp);
            } else {
                parameterType = parameterTypeRegistry.lookupByRegexp(parameterTypeRegexp, expressionRegexp, text);
                if (parameterType == null) {
                    parameterType = createAnonymousParameterType(parameterTypeRegexp);
                }
            }

            // Either from createAnonymousParameterType or lookupByRegexp
            if (parameterType.isAnonymous()) {
                parameterType = parameterType.deAnonymize(typeHint, arg -> defaultTransformer.transform(arg, typeHint));
            }

            parameterTypes.add(parameterType);
        }

        return Argument.build(group, treeRegexp, parameterTypes);
    }

    @Override
    public Pattern getRegexp() {
        return expressionRegexp;
    }

    @Override
    public String getSource() {
        return expressionRegexp.pattern();
    }
}
