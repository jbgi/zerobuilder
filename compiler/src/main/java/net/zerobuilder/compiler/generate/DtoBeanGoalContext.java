package net.zerobuilder.compiler.generate;

import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.FieldSpec;
import net.zerobuilder.compiler.analyse.DtoGoal.BeanGoal;
import net.zerobuilder.compiler.generate.DtoBeanStep.AbstractBeanStep;
import net.zerobuilder.compiler.generate.DtoBuilders.BuildersContext;
import net.zerobuilder.compiler.generate.DtoGoalContext.AbstractGoalContext;
import net.zerobuilder.compiler.generate.DtoGoalContext.GoalCases;

import static javax.lang.model.element.Modifier.PRIVATE;
import static net.zerobuilder.compiler.Utilities.downcase;
import static net.zerobuilder.compiler.Utilities.fieldSpec;

public final class DtoBeanGoalContext {

  public static abstract class BeanContext extends AbstractGoalContext {
    /**
     * alphabetic order unless {@link net.zerobuilder.Step} was used
     */
    final ImmutableList<? extends AbstractBeanStep> steps;
    final BeanGoal goal;
    final FieldSpec field;

    private BeanContext(BeanGoal goal,
                        boolean toBuilder,
                        boolean builder,
                        ImmutableList<? extends AbstractBeanStep> steps, FieldSpec field) {
      super(toBuilder, builder);
      this.steps = steps;
      this.goal = goal;
      this.field = field;
    }
  }

  public static final class BeanGoalContext extends BeanContext {

    final BuildersContext builders;

    private BeanGoalContext(BeanGoal goal,
                            BuildersContext builders,
                            boolean toBuilder,
                            boolean builder,
                            ImmutableList<? extends AbstractBeanStep> steps,
                            FieldSpec field) {
      super(goal, toBuilder, builder, steps, field);
      this.builders = builders;
    }

    public static BeanGoalContext create(BeanGoal goal,
                                         BuildersContext builders,
                                         boolean toBuilder,
                                         boolean builder,
                                         ImmutableList<? extends AbstractBeanStep> steps) {
      FieldSpec field = fieldSpec(goal.goalType,
          downcase(goal.goalType.simpleName()), PRIVATE);
      return new BeanGoalContext(goal, builders, toBuilder, builder, steps, field);
    }

    <R> R accept(GoalCases<R> cases) {
      return cases.beanGoal(this);
    }
  }

  private DtoBeanGoalContext() {
    throw new UnsupportedOperationException("no instances");
  }
}
