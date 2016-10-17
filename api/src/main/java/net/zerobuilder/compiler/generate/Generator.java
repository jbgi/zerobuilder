package net.zerobuilder.compiler.generate;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.TypeSpec;
import net.zerobuilder.compiler.generate.DtoContext.BuildersContext;
import net.zerobuilder.compiler.generate.DtoGeneratorOutput.BuilderMethod;
import net.zerobuilder.compiler.generate.DtoGeneratorOutput.GeneratorOutput;
import net.zerobuilder.compiler.generate.DtoGoalContext.AbstractGoalContext;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static net.zerobuilder.compiler.generate.DtoContext.BuilderLifecycle.NEW_INSTANCE;
import static net.zerobuilder.compiler.generate.DtoGoalContext.buildersContext;
import static net.zerobuilder.compiler.generate.GoalContextFactory.prepare;
import static net.zerobuilder.compiler.generate.Utilities.concat;
import static net.zerobuilder.compiler.generate.Utilities.downcase;
import static net.zerobuilder.compiler.generate.Utilities.flatList;
import static net.zerobuilder.compiler.generate.Utilities.transform;
import static net.zerobuilder.compiler.generate.Utilities.upcase;

public final class Generator {

  public static abstract class Module {
    public abstract BuilderMethod method(AbstractGoalContext goal);
    public abstract TypeSpec impl(AbstractGoalContext goal);
    public abstract String name();
    public abstract <R, P> R accept(ModuleCases<R, P> cases, P p);

    public final FieldSpec field(AbstractGoalContext goal) {
      ClassName type = builderImplType(goal);
      return FieldSpec.builder(type, downcase(type.simpleName()), PRIVATE, FINAL)
          .initializer("new $T()", type)
          .build();
    }

    private ClassName builderImplType(AbstractGoalContext goal) {
      String implName = Generator.implName.apply(this, goal);
      return buildersContext.apply(goal)
          .generatedType.nestedClass(implName);
    }

  }

  interface ModuleCases<R, P> {
    R simple(SimpleModule module, P p);
    R contract(ContractModule module, P p);
  }

  public static abstract class SimpleModule extends Module {

    @Override
    public final <R, P> R accept(ModuleCases<R, P> cases, P p) {
      return cases.simple(this, p);
    }
  }

  public static abstract class ContractModule extends Module {
    public abstract TypeSpec contract(AbstractGoalContext goal);

    @Override
    public final <R, P> R accept(ModuleCases<R, P> cases, P p) {
      return cases.contract(this, p);
    }
  }

  static <R, P> BiFunction<Module, P, R> asFunction(ModuleCases<R, P> cases) {
    return (module, p) -> module.accept(cases, p);
  }

  static <R, P> BiFunction<Module, P, R> moduleCases(
      BiFunction<SimpleModule, P, R> simple,
      BiFunction<ContractModule, P, R> contract) {
    return asFunction(new ModuleCases<R, P>() {
      @Override
      public R simple(SimpleModule module, P p) {
        return simple.apply(module, p);
      }
      @Override
      public R contract(ContractModule module, P p) {
        return contract.apply(module, p);
      }
    });
  }

  /**
   * Entry point for code generation.
   *
   * @param goals Goal descriptions
   * @return a GeneratorOutput
   */
  public static GeneratorOutput generate(GeneratorInput goals) {
    return generate(goals.context,
        transform(goals.goals, prepare(goals)));
  }

  private static GeneratorOutput generate(BuildersContext context, List<AbstractGoalContext> goals) {
    return new GeneratorOutput(
        methods(goals),
        nestedTypes(goals),
        fields(context, goals),
        context.generatedType,
        context.lifecycle);
  }

  private static List<FieldSpec> fields(BuildersContext context, List<AbstractGoalContext> goals) {
    return context.lifecycle == NEW_INSTANCE ?
        emptyList() :
        concat(
            context.cache.get(),
            goals.stream()
                .map(goal -> goal.module().module.field(goal))
                .collect(toList()));
  }

  private static List<BuilderMethod> methods(List<AbstractGoalContext> goals) {
    return goals.stream()
        .map(goal -> goal.module().module.method(goal))
        .collect(toList());
  }

  private static List<TypeSpec> nestedTypes(List<AbstractGoalContext> goals) {
    return goals.stream()
        .map(goal -> nestedTypes.apply(goal.module().module, goal))
        .collect(flatList());
  }

  private static final BiFunction<Module, AbstractGoalContext, List<TypeSpec>> nestedTypes =
      moduleCases(
          (simple, goal) -> singletonList(simple.impl(goal)),
          (contract, goal) -> Arrays.asList(contract.impl(goal), contract.contract(goal)));

  private static final BiFunction<Module, AbstractGoalContext, String> implName =
      moduleCases(
          (simple, goal) -> upcase(goal.name()) + upcase(simple.name()),
          (contract, goal) -> upcase(goal.name()) + upcase(contract.name()) + "Impl");

}
