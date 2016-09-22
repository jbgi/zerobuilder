package net.zerobuilder.compiler.generate;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import net.zerobuilder.compiler.generate.GoalContext.GoalCases;
import net.zerobuilder.compiler.analyse.GoalContextFactory.GoalKind;
import net.zerobuilder.compiler.generate.ParameterContext.BeansParameterContext;
import net.zerobuilder.compiler.generate.ParameterContext.ExecutableParameterContext;

import static com.google.common.collect.Iterables.getLast;
import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.MethodSpec.methodBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static com.squareup.javapoet.WildcardTypeName.subtypeOf;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;
import static net.zerobuilder.compiler.generate.GoalContext.builderImplName;
import static net.zerobuilder.compiler.generate.GoalContext.stepInterfaceNames;
import static net.zerobuilder.compiler.analyse.GoalContextFactory.GoalKind.INSTANCE_METHOD;
import static net.zerobuilder.compiler.generate.ParameterContext.maybeNullCheck;
import static net.zerobuilder.compiler.generate.ParameterContext.maybeIterationNullCheck;
import static net.zerobuilder.compiler.Utilities.downcase;
import static net.zerobuilder.compiler.Utilities.iterationVarName;
import static net.zerobuilder.compiler.Utilities.nullCheck;
import static net.zerobuilder.compiler.Utilities.parameterSpec;
import static net.zerobuilder.compiler.Utilities.upcase;

final class BuilderImplContext {

  private static final GoalCases<ImmutableList<FieldSpec>> fields
      = new GoalCases<ImmutableList<FieldSpec>>() {
    @Override
    ImmutableList<FieldSpec> executableGoal(GoalContext goal, TypeName goalType, GoalKind kind,
                                            ImmutableList<ExecutableParameterContext> parameters,
                                            ImmutableList<TypeName> thrownTypes) {
      ImmutableList.Builder<FieldSpec> builder = ImmutableList.builder();
      if (kind == INSTANCE_METHOD) {
        ClassName receiverType = goal.config.annotatedType;
        builder.add(FieldSpec.builder(receiverType, '_' + downcase(receiverType.simpleName()), PRIVATE).build());
      }
      for (ExecutableParameterContext parameter : parameters.subList(0, parameters.size() - 1)) {
        String name = parameter.parameter.name;
        builder.add(FieldSpec.builder(parameter.parameter.type, name, PRIVATE).build());
      }
      return builder.build();
    }
    @Override
    ImmutableList<FieldSpec> beanGoal(GoalContext goal, ClassName goalType, ImmutableList<BeansParameterContext> parameters) {
      FieldSpec field = FieldSpec.builder(goalType, downcase(goalType.simpleName()))
          .build();
      return ImmutableList.of(field);
    }
  };

  private static final GoalCases<ImmutableList<MethodSpec>> stepsButLast
      = new GoalCases<ImmutableList<MethodSpec>>() {
    @Override
    ImmutableList<MethodSpec> executableGoal(GoalContext goal, TypeName goalType, GoalKind kind,
                                             ImmutableList<ExecutableParameterContext> parameters,
                                             ImmutableList<TypeName> thrownTypes) {
      ImmutableList.Builder<MethodSpec> builder = ImmutableList.builder();
      for (ExecutableParameterContext parameter : parameters.subList(0, parameters.size() - 1)) {
        String name = parameter.parameter.name;
        CodeBlock finalBlock = CodeBlock.builder()
            .addStatement("this.$N = $N", name, name)
            .addStatement("return this")
            .build();
        builder.add(regularMethod(parameter, finalBlock, ImmutableList.<TypeName>of()));
      }
      return builder.build();
    }
    @Override
    ImmutableList<MethodSpec> beanGoal(GoalContext goal, ClassName goalType, ImmutableList<BeansParameterContext> parameters) {
      ImmutableList.Builder<MethodSpec> builder = ImmutableList.builder();
      CodeBlock finalBlock = CodeBlock.builder().addStatement("return this").build();
      for (BeansParameterContext parameter : parameters.subList(0, parameters.size() - 1)) {
        if (parameter.accessorPair.collectionType.type.isPresent()) {
          builder.addAll(beanCollectionMethods(parameter, goalType, finalBlock));
          if (parameter.accessorPair.collectionType.allowShortcut) {
            builder.add(beanCollectionShortcut(parameter, goalType, finalBlock));
          }
        } else {
          builder.add(beanRegularMethod(parameter, goalType, finalBlock));
        }
      }
      return builder.build();
    }
  };

  private static final GoalCases<ImmutableList<MethodSpec>> lastStep = new GoalCases<ImmutableList<MethodSpec>>() {
    @Override
    ImmutableList<MethodSpec> executableGoal(GoalContext goal, TypeName goalType, GoalKind kind,
                                             ImmutableList<ExecutableParameterContext> parameters,
                                             ImmutableList<TypeName> thrownTypes) {
      ExecutableParameterContext parameter = getLast(parameters);
      return ImmutableList.of(regularMethod(parameter, goal.goalCall, thrownTypes));
    }

    @Override
    ImmutableList<MethodSpec> beanGoal(GoalContext goal, ClassName goalType, ImmutableList<BeansParameterContext> parameters) {
      BeansParameterContext parameter = getLast(parameters);
      if (parameter.accessorPair.collectionType.type.isPresent()) {
        ImmutableList.Builder<MethodSpec> builder = ImmutableList.builder();
        builder.addAll(beanCollectionMethods(parameter, goalType, goal.goalCall));
        if (parameter.accessorPair.collectionType.allowShortcut) {
          builder.add(beanCollectionShortcut(parameter, goalType, goal.goalCall));
        }
        return builder.build();
      } else {
        return ImmutableList.of(beanRegularMethod(parameter, goalType, goal.goalCall));
      }
    }
  };

  static TypeSpec defineBuilderImpl(GoalContext goal) {
    return classBuilder(goal.accept(builderImplName))
        .addSuperinterfaces(goal.accept(stepInterfaceNames))
        .addFields(goal.accept(fields))
        .addMethod(constructorBuilder().addModifiers(PRIVATE).build())
        .addMethods(goal.accept(stepsButLast))
        .addMethods(goal.accept(lastStep))
        .addModifiers(FINAL, STATIC)
        .build();
  }

  private static MethodSpec regularMethod(ExecutableParameterContext parameter,
                                          CodeBlock finalBlock, ImmutableList<TypeName> thrownTypes) {
    String name = parameter.parameter.name;
    TypeName type = parameter.parameter.type;
    return methodBuilder(parameter.parameter.name)
        .addAnnotation(Override.class)
        .addParameter(parameterSpec(type, name))
        .addExceptions(thrownTypes)
        .addModifiers(PUBLIC)
        .returns(parameter.typeNextStep)
        .addCode(parameter.accept(maybeNullCheck))
        .addCode(finalBlock).build();
  }

  private static ImmutableList<MethodSpec> beanCollectionMethods(BeansParameterContext parameter, ClassName goalType, CodeBlock finalBlock) {
    String name = parameter.accessorPair.name;
    TypeName collectionType = parameter.accessorPair.collectionType.type.get();
    ParameterizedTypeName iterable = ParameterizedTypeName.get(ClassName.get(Iterable.class),
        subtypeOf(collectionType));
    MethodSpec fromIterable = methodBuilder(name)
        .addAnnotation(Override.class)
        .returns(parameter.typeNextStep)
        .addParameter(parameterSpec(iterable, name))
        .addCode(nullCheck(name, name))
        .beginControlFlow("for ($T $N : $N)",
            collectionType, iterationVarName, name)
        .addCode(parameter.accept(maybeIterationNullCheck))
        .addStatement("this.$N.$N().add($N)", downcase(goalType.simpleName()),
            parameter.accessorPair.projectionMethodName, iterationVarName)
        .endControlFlow()
        .addCode(finalBlock)
        .addModifiers(PUBLIC)
        .build();
    MethodSpec fromEmpty = methodBuilder(name)
        .addAnnotation(Override.class)
        .returns(parameter.typeNextStep)
        .addCode(finalBlock)
        .addModifiers(PUBLIC)
        .build();
    return ImmutableList.of(fromIterable, fromEmpty);
  }

  private static MethodSpec beanCollectionShortcut(BeansParameterContext parameter, ClassName goalType, CodeBlock finalBlock) {
    String name = parameter.accessorPair.name;
    TypeName collectionType = parameter.accessorPair.collectionType.type.get();
    return methodBuilder(name)
        .addAnnotation(Override.class)
        .returns(parameter.typeNextStep)
        .addParameter(parameterSpec(collectionType, name))
        .addCode(parameter.accept(maybeNullCheck))
        .addStatement("this.$N.$N().add($N)", downcase(goalType.simpleName()),
            parameter.accessorPair.projectionMethodName, name)
        .addCode(finalBlock)
        .addModifiers(PUBLIC)
        .build();
  }

  private static MethodSpec beanRegularMethod(BeansParameterContext parameter, ClassName goalType, CodeBlock finalBlock) {
    String name = parameter.accessorPair.name;
    TypeName type = parameter.accessorPair.type;
    return methodBuilder(parameter.accessorPair.name)
        .addAnnotation(Override.class)
        .addParameter(parameterSpec(type, name))
        .addModifiers(PUBLIC)
        .returns(parameter.typeNextStep)
        .addCode(parameter.accept(maybeNullCheck))
        .addStatement("this.$N.set$L($N)", downcase(goalType.simpleName()), upcase(name), name)
        .addCode(finalBlock).build();
  }

  private BuilderImplContext() {
    throw new UnsupportedOperationException("no instances");
  }
}