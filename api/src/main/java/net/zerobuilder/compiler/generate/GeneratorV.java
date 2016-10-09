package net.zerobuilder.compiler.generate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import net.zerobuilder.compiler.generate.DtoGeneratorOutput.BuilderMethod;
import net.zerobuilder.compiler.generate.DtoProjectionInfo.FieldAccess;
import net.zerobuilder.compiler.generate.DtoProjectionInfo.ProjectionInfo;
import net.zerobuilder.compiler.generate.DtoProjectionInfo.ProjectionInfoRequiredCases;
import net.zerobuilder.compiler.generate.DtoProjectionInfo.ProjectionMethod;
import net.zerobuilder.compiler.generate.DtoRegularGoalContext.RegularGoalContext;
import net.zerobuilder.compiler.generate.DtoStep.RegularStep;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static javax.lang.model.element.Modifier.STATIC;
import static net.zerobuilder.NullPolicy.ALLOW;
import static net.zerobuilder.compiler.generate.DtoGoalContext.builderImplType;
import static net.zerobuilder.compiler.generate.DtoProjectionInfo.thrownTypes;
import static net.zerobuilder.compiler.generate.DtoRegularGoalContext.isInstance;
import static net.zerobuilder.compiler.generate.DtoRegularGoalContext.regularSteps;
import static net.zerobuilder.compiler.generate.Generator.stepsField;
import static net.zerobuilder.compiler.generate.Generator.updaterField;
import static net.zerobuilder.compiler.generate.UpdaterContext.updaterType;
import static net.zerobuilder.compiler.generate.Utilities.downcase;
import static net.zerobuilder.compiler.generate.Utilities.emptyCodeBlock;
import static net.zerobuilder.compiler.generate.Utilities.parameterSpec;
import static net.zerobuilder.compiler.generate.Utilities.statement;

final class GeneratorV {

  static final Function<RegularGoalContext, BuilderMethod> goalToToBuilder =
      goal -> {
        DtoGoal.RegularGoalDetails regularGoalDetails = DtoRegularGoalContext.regularGoal.apply(goal);
        TypeName goalType = regularGoalDetails.goalType;
        ParameterSpec parameter = parameterSpec(goalType, downcase(((ClassName) goalType.box()).simpleName()));
        String name = regularGoalDetails.name;
        String methodName = name + "ToBuilder";
        ParameterSpec updater = updaterInstance(goal);
        MethodSpec.Builder method = methodBuilder(methodName)
            .addExceptions(thrownByProjections(goal))
            .addParameter(parameter)
            .returns(updater.type)
            .addCode(initializeUpdater(goal, updater));
        for (RegularStep step : regularSteps.apply(goal)) {
          Function<ProjectionInfo, CodeBlock> copy = copyField(parameter, updater, step);
          method.addCode(copy.apply(step.validParameter.projectionInfo));
        }
        method.addStatement("return $N", updater);
        MethodSpec methodSpec = method.addModifiers(regularGoalDetails.goalOptions.toBuilderAccess.modifiers(STATIC))
            .build();
        return new BuilderMethod(name, methodSpec);
      };

  private static Function<ProjectionInfo, CodeBlock> copyField(ParameterSpec parameter, ParameterSpec updater, RegularStep step) {
    return DtoProjectionInfo.asFunction(new ProjectionInfoRequiredCases<CodeBlock>() {
      @Override
      public CodeBlock projectionMethod(ProjectionMethod projection) {
        CodeBlock.Builder builder = CodeBlock.builder();
        String field = step.validParameter.name;
        builder.add(nullCheckGetter(parameter, step, projection.methodName))
            .addStatement("$N.$N = $N.$N()",
                updater, field, parameter, projection.methodName);
        return builder.build();
      }
      @Override
      public CodeBlock fieldAccess(FieldAccess projection) {
        CodeBlock.Builder builder = CodeBlock.builder();
        String field = projection.fieldName;
        builder.add(nullCheckFieldAccess(parameter, step))
            .addStatement("$N.$N = $N.$N",
                updater, field, parameter, field);
        return builder.build();
      }
    });
  }

  private static CodeBlock nullCheckFieldAccess(ParameterSpec parameter, RegularStep step) {
    if (step.validParameter.nullPolicy == ALLOW) {
      return emptyCodeBlock;
    }
    String name = step.validParameter.name;
    return CodeBlock.builder()
        .beginControlFlow("if ($N.$N == null)", parameter, name)
        .addStatement("throw new $T($S)", NullPointerException.class, name)
        .endControlFlow().build();
  }

  private static CodeBlock nullCheckGetter(ParameterSpec parameter, RegularStep step, String getter) {
    if (step.validParameter.nullPolicy == ALLOW) {
      return emptyCodeBlock;
    }
    String name = step.validParameter.name;
    return CodeBlock.builder()
        .beginControlFlow("if ($N.$N() == null)", parameter, getter)
        .addStatement("throw new $T($S)", NullPointerException.class, name)
        .endControlFlow().build();
  }

  private static CodeBlock initializeUpdater(RegularGoalContext goal, ParameterSpec updater) {
    CodeBlock.Builder builder = CodeBlock.builder();
    DtoBuildersContext.BuildersContext buildersContext = DtoRegularGoalContext.buildersContext.apply(goal);
    boolean recycle = buildersContext.lifecycle.recycle();
    if (recycle) {
      FieldSpec cache = buildersContext.cache;
      String updaterField = updaterField(goal);
      builder.addStatement("$T $N = $N.get().$N", updater.type, updater, cache, updaterField);
    } else {
      builder.addStatement("$T $N = new $T()", updater.type, updater, updater.type);
    }
    return builder.build();
  }

  private static ParameterSpec updaterInstance(RegularGoalContext goal) {
    ClassName updaterType = updaterType(goal);
    return parameterSpec(updaterType, "updater");
  }


  static final Function<RegularGoalContext, BuilderMethod> goalToBuilder
      = goal -> {
    DtoGoal.RegularGoalDetails regularGoalDetails = DtoRegularGoalContext.regularGoal.apply(goal);
    List<RegularStep> steps = regularSteps.apply(goal);
    String name = DtoGoalContext.goalName.apply(goal);
    MethodSpec.Builder method = methodBuilder(name + "Builder")
        .returns(steps.get(0).thisType)
        .addModifiers(regularGoalDetails.goalOptions.builderAccess.modifiers(STATIC));
    ParameterSpec builder = builderInstance(goal);
    method.addCode(initBuilder(goal, builder));
    if (isInstance.test(goal)) {
      DtoBuildersContext.BuildersContext buildersContext = DtoRegularGoalContext.buildersContext.apply(goal);
      ParameterSpec parameter = parameterSpec(buildersContext.type,
          downcase(buildersContext.type.simpleName()));
      method.addParameter(parameter)
          .addStatement("$N.$N = $N", builder, buildersContext.field, parameter);
    }
    MethodSpec methodSpec = method.addStatement("return $N", builder).build();
    return new BuilderMethod(name, methodSpec);
  };

  private static CodeBlock initBuilder(RegularGoalContext goal, ParameterSpec builder) {
    DtoBuildersContext.BuildersContext buildersContext = DtoRegularGoalContext.buildersContext.apply(goal);
    return buildersContext.lifecycle.recycle()
        ? statement("$T $N = $N.get().$N", builder.type, builder, buildersContext.cache, stepsField(goal))
        : statement("$T $N = new $T()", builder.type, builder, builder.type);
  }

  private static ParameterSpec builderInstance(RegularGoalContext goal) {
    ClassName stepsType = builderImplType(goal);
    return parameterSpec(stepsType, downcase(stepsType.simpleName()));
  }

  private static Set<TypeName> thrownByProjections(RegularGoalContext goal) {
    return regularSteps.apply(goal).stream()
        .map(step -> step.validParameter)
        .map(parameter -> parameter.projectionInfo)
        .map(thrownTypes)
        .map(List::stream)
        .flatMap(Function.identity())
        .collect(Collectors.toSet());
  }

  private GeneratorV() {
    throw new UnsupportedOperationException("no instances");
  }
}
