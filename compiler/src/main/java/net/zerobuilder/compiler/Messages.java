package net.zerobuilder.compiler;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.squareup.javapoet.AnnotationSpec;

import javax.annotation.Generated;
import javax.lang.model.util.Elements;

final class Messages {

  static final class ErrorMessages {

    static final String PRIVATE_METHOD =
        "The goal may not be private.";

    static final String PRIVATE_TYPE =
        "The @Build annotated type may not be private.";

    static final String NOT_ENOUGH_PARAMETERS =
        "The goal has no parameters.";

    static final String NESTING_KIND =
        "The @Build annotation can only be used on top level and non-private static inner classes.";

    static final String SEVERAL_GOAL_ANNOTATIONS =
        "The @Goal annotation may not appear more than once per class.";

    static final String COULD_NOT_GUESS_GOAL =
        "Could not guess the @Goal method. Please add a @Goal annotation.";

    private ErrorMessages() {
    }

  }

  static final class JavadocMessages {

    static final String JAVADOC_BUILDER = Joiner.on('\n').join(ImmutableList.of(
        "First step of the builder chain that builds {@link $T}.",
        "@return A builder object", ""));

    static final String GENERATED_COMMENTS = "https://github.com/h908714124/zerobuilder";

    static ImmutableList<AnnotationSpec> generatedAnnotations(Elements elements) {
      if (elements.getTypeElement("javax.annotation.Generated") != null) {
        return ImmutableList.of(AnnotationSpec.builder(Generated.class)
            .addMember("value", "$S", ZeroProcessor.class.getName())
            .addMember("comments", "$S", GENERATED_COMMENTS)
            .build());
      }
      return ImmutableList.of();

    }

    private JavadocMessages() {
    }

  }

  private Messages() {
  }

}