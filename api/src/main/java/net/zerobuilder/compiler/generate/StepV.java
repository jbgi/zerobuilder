package net.zerobuilder.compiler.generate;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import net.zerobuilder.compiler.generate.DtoParameter.AbstractParameter;
import net.zerobuilder.compiler.generate.DtoStep.RegularStep;

import java.util.Optional;
import java.util.function.Function;

import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.interfaceBuilder;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;
import static net.zerobuilder.compiler.generate.DtoParameter.parameterName;
import static net.zerobuilder.compiler.generate.DtoStep.abstractParameter;
import static net.zerobuilder.compiler.generate.Utilities.parameterSpec;
import static net.zerobuilder.compiler.generate.Utilities.presentInstances;

final class StepV {

  static final Function<RegularStep, TypeSpec> regularStepInterface
      = step -> interfaceBuilder(step.thisType)
      .addMethod(regularStepMethod(step))
      .addMethods(presentInstances(emptyCollection(step)))
      .addModifiers(PUBLIC)
      .build();

  private static MethodSpec regularStepMethod(RegularStep step) {
    AbstractParameter parameter = abstractParameter.apply(step);
    String name = parameterName.apply(parameter);
    TypeName type = parameter.type;
    return methodBuilder(name)
        .returns(step.nextType)
        .addParameter(parameterSpec(type, name))
        .addExceptions(step.declaredExceptions)
        .addModifiers(PUBLIC, ABSTRACT)
        .build();
  }

  private static Optional<MethodSpec> emptyCollection(RegularStep step) {
    Optional<DtoStep.CollectionInfo> maybeEmptyOption = step.emptyOption();
    if (!maybeEmptyOption.isPresent()) {
      return Optional.empty();
    }
    DtoStep.CollectionInfo collectionInfo = maybeEmptyOption.get();
    return Optional.of(methodBuilder(collectionInfo.name)
        .returns(step.nextType)
        .addModifiers(PUBLIC, ABSTRACT)
        .build());
  }

  private StepV() {
    throw new UnsupportedOperationException("no instances");
  }
}