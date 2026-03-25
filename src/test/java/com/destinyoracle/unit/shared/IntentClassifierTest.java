package com.destinyoracle.unit.shared;

import com.destinyoracle.shared.ai.IntentClassifier;
import com.destinyoracle.shared.ai.IntentClassifier.Intent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @Test
    void classify_reminderRequest_returnsReminder() {
        assertThat(classifier.classify("remind me to buy groceries tomorrow at 10am"))
            .isEqualTo(Intent.REMINDER);
        assertThat(classifier.classify("set an alarm for me at 8am"))
            .isEqualTo(Intent.REMINDER);
    }

    @Test
    void classify_taskRequest_returnsTask() {
        assertThat(classifier.classify("create a workout plan for legs"))
            .isEqualTo(Intent.TASK);
        assertThat(classifier.classify("give me a meal plan for this week"))
            .isEqualTo(Intent.TASK);
    }

    @Test
    void classify_planSave_returnsPlanSave() {
        assertThat(classifier.classify("save this as my leg day"))
            .isEqualTo(Intent.PLAN_SAVE);
        assertThat(classifier.classify("save this plan"))
            .isEqualTo(Intent.PLAN_SAVE);
    }

    @Test
    void classify_planQuery_returnsPlanQuery() {
        assertThat(classifier.classify("what's my leg day workout"))
            .isEqualTo(Intent.PLAN_QUERY);
        assertThat(classifier.classify("show my routine"))
            .isEqualTo(Intent.PLAN_QUERY);
    }

    @Test
    void classify_insightRequest_returnsInsight() {
        assertThat(classifier.classify("how was my day today"))
            .isEqualTo(Intent.INSIGHT);
        assertThat(classifier.classify("daily summary please"))
            .isEqualTo(Intent.INSIGHT);
    }

    @Test
    void classify_generalChat_returnsGeneral() {
        assertThat(classifier.classify("hello how are you"))
            .isEqualTo(Intent.GENERAL);
        assertThat(classifier.classify("what's the weather like"))
            .isEqualTo(Intent.GENERAL);
    }

    @Test
    void classify_nullOrEmpty_returnsGeneral() {
        assertThat(classifier.classify(null)).isEqualTo(Intent.GENERAL);
        assertThat(classifier.classify("")).isEqualTo(Intent.GENERAL);
        assertThat(classifier.classify("   ")).isEqualTo(Intent.GENERAL);
    }
}
