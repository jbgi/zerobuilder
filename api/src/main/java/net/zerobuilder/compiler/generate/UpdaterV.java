package net.zerobuilder.compiler.generate;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import net.zerobuilder.compiler.generate.DtoRegularGoal.AbstractRegularGoalContext;
import net.zerobuilder.compiler.generate.DtoRegularStep.AbstractRegularStep;
import net.zerobuilder.compiler.generate.DtoStep.CollectionInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static net.zerobuilder.compiler.generate.Step.nullCheck;
import static net.zerobuilder.compiler.generate.Utilities.fieldSpec;
import static net.zerobuilder.compiler.generate.Utilities.flatList;
import static net.zerobuilder.compiler.generate.Utilities.parameterSpec;
import static net.zerobuilder.compiler.generate.Utilities.presentInstances;

final class UpdaterV {

  private final Updater updater;

  UpdaterV(Updater updater) {
    this.updater = updater;
  }

  final Function<AbstractRegularGoalContext, List<FieldSpec>> fieldsV
      = goal -> {
    List<FieldSpec> builder = new ArrayList<>();
    builder.addAll(presentInstances(goal.fields()));
    for (AbstractRegularStep step : goal.regularSteps()) {
      String name = step.regularParameter().name;
      TypeName type = step.regularParameter().type;
      builder.add(fieldSpec(type, name, PRIVATE));
    }
    return builder;
  };

  final Function<AbstractRegularGoalContext, List<MethodSpec>> updateMethodsV
      = goal ->
      goal.regularSteps().stream()
          .map(updateMethods(goal))
          .collect(flatList());


  private Function<AbstractRegularStep, List<MethodSpec>> updateMethods(AbstractRegularGoalContext goal) {
    return step -> Stream.concat(
        Stream.of(normalUpdate(goal, step)),
        presentInstances(emptyCollection(goal, step)).stream())
        .collect(toList());
  }

  private Optional<MethodSpec> emptyCollection(AbstractRegularGoalContext goal, AbstractRegularStep step) {
    Optional<CollectionInfo> maybeEmptyOption = step.collectionInfo();
    if (!maybeEmptyOption.isPresent()) {
      return Optional.empty();
    }
    CollectionInfo collectionInfo = maybeEmptyOption.get();
    return Optional.of(methodBuilder(collectionInfo.name)
        .returns(updater.implType(goal))
        .addStatement("this.$N = $L",
            step.field(), collectionInfo.initializer)
        .addStatement("return this")
        .addModifiers(PUBLIC)
        .build());
  }

  private MethodSpec normalUpdate(AbstractRegularGoalContext goal, AbstractRegularStep step) {
    String name = step.regularParameter().name;
    TypeName type = step.regularParameter().type;
    ParameterSpec parameter = parameterSpec(type, name);
    return methodBuilder(name)
        .returns(updater.implType(goal))
        .addParameter(parameter)
        .addCode(nullCheck.apply(step))
        .addStatement("this.$N = $N", step.field(), parameter)
        .addStatement("return this")
        .addModifiers(PUBLIC)
        .build();
  }
}
