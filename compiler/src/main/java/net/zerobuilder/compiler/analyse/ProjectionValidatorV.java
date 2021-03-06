package net.zerobuilder.compiler.analyse;

import com.squareup.javapoet.TypeName;
import net.zerobuilder.compiler.analyse.DtoGoalElement.RegularGoalElement;
import net.zerobuilder.compiler.analyse.ProjectionValidator.TmpProjectedParameter;
import net.zerobuilder.compiler.analyse.ProjectionValidator.TmpSimpleParameter;
import net.zerobuilder.compiler.generate.DtoGoalDescription.GoalDescription;
import net.zerobuilder.compiler.generate.DtoProjectionInfo.ProjectionInfo;
import net.zerobuilder.compiler.generate.DtoRegularGoalDescription.AbstractRegularGoalDescription;
import net.zerobuilder.compiler.generate.DtoRegularGoalDescription.ProjectedRegularGoalDescription;
import net.zerobuilder.compiler.generate.DtoRegularGoalDescription.SimpleRegularGoalDescription;
import net.zerobuilder.compiler.generate.DtoRegularParameter.ProjectedParameter;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static net.zerobuilder.compiler.Messages.ErrorMessages.ABSTRACT_CONSTRUCTOR;
import static net.zerobuilder.compiler.Messages.ErrorMessages.NO_PROJECTION;
import static net.zerobuilder.compiler.analyse.ProjectionValidator.TmpProjectedParameter.toValidParameter;
import static net.zerobuilder.compiler.analyse.ProjectionValidator.shuffledParameters;
import static net.zerobuilder.compiler.analyse.Utilities.findKey;
import static net.zerobuilder.compiler.analyse.Utilities.thrownTypes;
import static net.zerobuilder.compiler.analyse.Utilities.transform;
import static net.zerobuilder.compiler.analyse.Utilities.upcase;
import static net.zerobuilder.compiler.common.LessElements.getLocalAndInheritedMethods;
import static net.zerobuilder.compiler.common.LessTypes.asTypeElement;
import static net.zerobuilder.compiler.generate.DtoProjectionInfo.fieldAccess;
import static net.zerobuilder.compiler.generate.DtoProjectionInfo.method;

final class ProjectionValidatorV {

  private static final Predicate<ExecutableElement> LOOKS_LIKE_PROJECTION = method -> method.getParameters().isEmpty()
      && !method.getModifiers().contains(PRIVATE)
      && !method.getModifiers().contains(STATIC)
      && method.getReturnType().getKind() != TypeKind.VOID
      && !"getClass".equals(method.getSimpleName().toString())
      && !"clone".equals(method.getSimpleName().toString());

  static final Function<RegularGoalElement, GoalDescription> validateValue
      = goal -> {
    TypeElement type = asTypeElement(goal.executableElement.getEnclosingElement().asType());
    validateType(goal, type);
    Map<String, ExecutableElement> methods = projectionCandidates(type);
    Map<String, VariableElement> fields = fields(type);
    List<TmpProjectedParameter> parameters = transform(goal.executableElement.getParameters(),
        parameter -> TmpProjectedParameter.create(parameter,
            projectionInfo(methods, fields, parameter),
            goal.goalAnnotation));
    return createGoalDescription(goal, parameters);
  };

  private static ProjectionInfo projectionInfo(Map<String, ExecutableElement> methods,
                                               Map<String, VariableElement> fields,
                                               VariableElement parameter) {
    String name = parameter.getSimpleName().toString();
    VariableElement field = fields.get(name);
    if (field != null && TypeName.get(field.asType()).equals(TypeName.get(parameter.asType()))) {
      return fieldAccess(field.getSimpleName().toString());
    }
    List<String> possibleNames = Arrays.asList("get" + upcase(name), "is" + upcase(name), name);
    return findKey(methods, possibleNames)
        .map(methodName -> method(methodName, thrownTypes(methods.get(methodName))))
        .orElseThrow(() -> new ValidationException(NO_PROJECTION, parameter));
  }


  private static void validateType(RegularGoalElement goal,
                                   TypeElement type) {
    if (goal.executableElement.getKind() == ElementKind.CONSTRUCTOR
        && type.getModifiers().contains(ABSTRACT)) {
      throw new ValidationException(ABSTRACT_CONSTRUCTOR, goal.executableElement);
    }
  }

  private static Map<String, VariableElement> fields(TypeElement type) {
    List<VariableElement> variableElements = fieldsIn(type.getEnclosedElements());
    Map<String, VariableElement> map = new HashMap<>();
    variableElements.stream()
        .filter(field -> !field.getModifiers().contains(PRIVATE)
            && !field.getModifiers().contains(STATIC))
        .forEach(field -> map.computeIfAbsent(
            field.getSimpleName().toString(), name -> field));
    return map;
  }

  private static Map<String, ExecutableElement> projectionCandidates(TypeElement type) {
    return getLocalAndInheritedMethods(type, LOOKS_LIKE_PROJECTION);
  }

  static final Function<RegularGoalElement, GoalDescription> validateValueIgnoreProjections
      = goal -> {
    List<TmpSimpleParameter> parameters = goal.executableElement.getParameters()
        .stream()
        .map(parameter -> TmpSimpleParameter.create(parameter, goal.goalAnnotation))
        .collect(toList());
    List<TmpSimpleParameter> shuffled = shuffledParameters(parameters);
    return SimpleRegularGoalDescription.create(
        goal.details,
        thrownTypes(goal.executableElement),
        transform(shuffled, parameter -> parameter.parameter));
  };

  private static GoalDescription createGoalDescription(RegularGoalElement goal,
                                                       List<TmpProjectedParameter> parameters) {
    List<TmpProjectedParameter> shuffled = shuffledParameters(parameters);
    return create(goal, transform(shuffled, toValidParameter));
  }

  private static AbstractRegularGoalDescription create(RegularGoalElement goal,
                                                       List<ProjectedParameter> parameters) {
    return ProjectedRegularGoalDescription.create(
        goal.details, thrownTypes(goal.executableElement),
        parameters);
  }

  private ProjectionValidatorV() {
    throw new UnsupportedOperationException("no instances");
  }
}
