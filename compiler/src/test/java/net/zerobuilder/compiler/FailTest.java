package net.zerobuilder.compiler;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.util.Arrays;
import java.util.List;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaFileObjects.forSourceLines;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static java.util.Collections.singletonList;

public class FailTest {

  @Test
  public void goalNotInBuild() {
    String badLine = "  @Goal static void create(int numberOfLegs) {}";
    List<String> sourceLines = Arrays.asList(
        "package test;",
        "import net.zerobuilder.Goal;",
        "class Centipede {",
        badLine,
        "}");
    JavaFileObject javaFile = forSourceLines("test.Centipede", sourceLines);
    int line = sourceLines.indexOf(badLine);
    assertAbout(javaSources()).that(ImmutableList.of(javaFile))
        .processedWith(new ZeroProcessor())
        .failsToCompile()
        .withErrorContaining("outside")
        .in(javaFile)
        .onLine(line + 1);
  }

  @Test
  public void twoUnnamedConstructors() {
    String badLine = "  @Goal Centipede(int a) {}";
    List<String> sourceLines = Arrays.asList(
        "package test;",
        "import net.zerobuilder.*;",
        "@Builders class Centipede {",
        "  @Goal Centipede(int a, int b) {}",
        badLine,
        "}");
    JavaFileObject javaFile = forSourceLines("test.Centipede", sourceLines);
    int line = sourceLines.indexOf(badLine);
    assertAbout(javaSources()).that(singletonList(javaFile))
        .processedWith(new ZeroProcessor())
        .failsToCompile()
        .withErrorContaining("Multiple constructor goals")
        .in(javaFile)
        .onLine(line + 1);
  }

  @Test
  public void constructorVersusFactory() {
    String badLine = "  @Goal static Centipede create (int a) {}";
    List<String> sourceLines = Arrays.asList(
        "package test;",
        "import net.zerobuilder.*;",
        "@Builders class Centipede {",
        "  @Goal Centipede(int a) {}",
        badLine,
        "}");
    JavaFileObject javaFile = forSourceLines("test.Centipede", sourceLines);
    int line = sourceLines.indexOf(badLine);
    assertAbout(javaSources()).that(ImmutableList.of(javaFile))
        .processedWith(new ZeroProcessor())
        .failsToCompile()
        .withErrorContaining("already a constructor goal")
        .in(javaFile)
        .onLine(line + 1);
  }

}
