package net.zerobuilder.compiler.generate;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import net.zerobuilder.compiler.generate.DtoContext.BuilderLifecycle;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import static com.squareup.javapoet.MethodSpec.constructorBuilder;
import static com.squareup.javapoet.TypeSpec.classBuilder;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static net.zerobuilder.compiler.generate.DtoContext.BuilderLifecycle.NEW_INSTANCE;
import static net.zerobuilder.compiler.generate.DtoGeneratorOutput.BuilderMethod.getMethod;
import static net.zerobuilder.compiler.generate.Utilities.concat;
import static net.zerobuilder.compiler.generate.Utilities.transform;

public final class DtoGeneratorOutput {

  /**
   * Can be either a {@code builder} or {@code updater} method
   */
  public static final class BuilderMethod {

    private final String name;
    private final MethodSpec method;

    BuilderMethod(String name, MethodSpec method) {
      this.name = name;
      this.method = method;
    }

    /**
     * Returns the name of the goal that generates this method.
     *
     * @return goal name
     */
    public String name() {
      return name;
    }

    public MethodSpec method() {
      return method;
    }

    static final Function<BuilderMethod, MethodSpec> getMethod
        = builderMethod -> builderMethod.method;
  }

  public static final class GeneratorOutput {

    final List<BuilderMethod> methods;
    final List<TypeSpec> nestedTypes;
    final List<FieldSpec> fields;
    final ClassName generatedType;
    final BuilderLifecycle lifecycle;

    public GeneratorOutput(List<BuilderMethod> methods, List<TypeSpec> nestedTypes, List<FieldSpec> fields,
                           ClassName generatedType, BuilderLifecycle lifecycle) {
      this.methods = methods;
      this.nestedTypes = nestedTypes;
      this.fields = fields;
      this.generatedType = generatedType;
      this.lifecycle = lifecycle;
    }

    /**
     * Create the definition of the generated class.
     *
     * @param generatedAnnotations annotations to add to the generated type, if any
     * @return type definition
     */
    public TypeSpec typeSpec(List<AnnotationSpec> generatedAnnotations) {
      return classBuilder(generatedType)
          .addFields(fields)
          .addMethod(constructor())
          .addMethods(transform(methods(), getMethod))
          .addAnnotations(generatedAnnotations)
          .addModifiers(PUBLIC, FINAL)
          .addTypes(nestedTypes)
          .build();
    }

    private MethodSpec constructor() {
      return lifecycle == BuilderLifecycle.REUSE_INSTANCES ?
          Utilities.constructor(PRIVATE) :
          constructorBuilder()
              .addStatement("throw new $T($S)", UnsupportedOperationException.class, "no instances")
              .addModifiers(PRIVATE)
              .build();
    }

    /**
     * All methods in the type returned by {@link #typeSpec(List)}.
     * Includes static methods. Excludes constructors.
     *
     * @return list of methods
     */
    public List<BuilderMethod> methods() {
      return methods;
    }

    /**
     * Create the definition of the generated class.
     *
     * @return type definition
     */
    public TypeSpec typeSpec() {
      return typeSpec(emptyList());
    }

    /**
     * All types that are nested directly inside the type returned by {@link #typeSpec(List)}.
     * Excludes non-static inner classes, local classes and anonymous classes.
     *
     * @return list of types
     */
    public List<TypeSpec> nestedTypes() {
      return nestedTypes;
    }

    /**
     * Class name of the type returned by {@link #typeSpec(List)}.
     *
     * @return class name
     */
    public ClassName generatedType() {
      return generatedType;
    }
  }

  static Collector<SingleModuleOutputWithField, List<SingleModuleOutputWithField>, GeneratorOutput> collectOutput(
      DtoContext.BuildersContext context) {
    return new Collector<SingleModuleOutputWithField, List<SingleModuleOutputWithField>, GeneratorOutput>() {
      @Override
      public Supplier<List<SingleModuleOutputWithField>> supplier() {
        return ArrayList::new;
      }
      @Override
      public BiConsumer<List<SingleModuleOutputWithField>, SingleModuleOutputWithField> accumulator() {
        return (left, right) -> left.add(right);
      }
      @Override
      public BinaryOperator<List<SingleModuleOutputWithField>> combiner() {
        return (left, right) -> {
          left.addAll(right);
          return left;
        };
      }
      @Override
      public Function<List<SingleModuleOutputWithField>, GeneratorOutput> finisher() {
        return outputs -> {
          List<BuilderMethod> methods = new ArrayList<>(outputs.size());
          List<TypeSpec> nestedTypes = new ArrayList<>();
          List<FieldSpec> fields = new ArrayList<>();
          if (context.lifecycle == BuilderLifecycle.REUSE_INSTANCES) {
            fields.add(context.cache.get());
          }
          for (SingleModuleOutputWithField output : outputs) {
            methods.add(output.output.method);
            output.field.ifPresent(fields::add);
            output.output.nestedTypes.forEach(nestedTypes::add);
          }
          return new GeneratorOutput(methods, nestedTypes, fields, context.generatedType, context.lifecycle);
        };
      }
      @Override
      public Set<Characteristics> characteristics() {
        return emptySet();
      }
    };
  }

  public static final class SingleModuleOutput {
    final BuilderMethod method;
    final List<TypeSpec> nestedTypes;

    public SingleModuleOutput(BuilderMethod method, List<TypeSpec> nestedTypes) {
      this.method = method;
      this.nestedTypes = nestedTypes;
    }
  }

  static final class SingleModuleOutputWithField {
    final SingleModuleOutput output;
    final Optional<FieldSpec> field;

    SingleModuleOutputWithField(SingleModuleOutput output, Optional<FieldSpec> field) {
      this.output = output;
      this.field = field;
    }
  }

  private DtoGeneratorOutput() {
    throw new UnsupportedOperationException("no instances");
  }
}
