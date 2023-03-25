package de.tum.in.www1.artemis.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.config.Constants;

class FeedbackTest {

    @Test
    void setShortDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText("abc");

        assertThat(feedback.getDetailText()).isEqualTo("abc");
        assertThat(feedback.getLongFeedbackText()).isNull();
        assertThat(feedback.hasLongFeedbackText()).isFalse();
    }

    @Test
    void setNullDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(null);

        assertThat(feedback.getDetailText()).isNull();
        assertThat(feedback.getLongFeedbackText()).isNull();
        assertThat(feedback.hasLongFeedbackText()).isFalse();
    }

    @Test
    void setLongDetailText() {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(getText(Constants.FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS + 10));

        assertThat(feedback.getDetailText()).hasSize(Constants.FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS);
        assertThat(feedback.hasLongFeedbackText()).isTrue();

        final LongFeedbackText longFeedbackText = feedback.getLongFeedbackText();
        assertThat(longFeedbackText.getFeedback()).isSameAs(feedback);
        assertThat(longFeedbackText.getText()).hasSize(Constants.FEEDBACK_DETAIL_TEXT_MAX_CHARACTERS + 10);
    }

    private String getText(final int length) {
        return "0".repeat(length);
    }
}
