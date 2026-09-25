package com.parallax.engine.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentsTest {

    @Test
    void scoreBandBoundaries() {
        assertThat(Segments.scoreBand(300)).isEqualTo("<620");
        assertThat(Segments.scoreBand(619)).isEqualTo("<620");
        assertThat(Segments.scoreBand(620)).isEqualTo("620–679");
        assertThat(Segments.scoreBand(679)).isEqualTo("620–679");
        assertThat(Segments.scoreBand(680)).isEqualTo("680–719");
        assertThat(Segments.scoreBand(719)).isEqualTo("680–719");
        assertThat(Segments.scoreBand(720)).isEqualTo("720–759");
        assertThat(Segments.scoreBand(759)).isEqualTo("720–759");
        assertThat(Segments.scoreBand(760)).isEqualTo("760+");
        assertThat(Segments.scoreBand(850)).isEqualTo("760+");
    }
}
