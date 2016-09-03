package net.zerobuilder.examples.autovalue;

import com.google.auto.value.AutoValue;
import net.zerobuilder.Build;

@AutoValue
@Build
abstract class Bob {

  abstract String kevin();
  abstract String chantal();
  abstract String justin();

  @Build.Goal(toBuilder = true)
  static Bob create(String kevin, String chantal, String justin) {
    return new AutoValue_Bob(kevin, chantal, justin);
  }

  BobBuilders.BobBuilder.Contract.BobUpdater toBuilder() {
    return BobBuilders.toBuilder(this);
  }

  Bob withChantal(String chantal) {
    return toBuilder().chantal(chantal).build();
  }

  Bob withKevin(String kevin) {
    return toBuilder().kevin(kevin).build();
  }

  Bob withJustin(String justin) {
    return toBuilder().justin(justin).build();
  }

}
