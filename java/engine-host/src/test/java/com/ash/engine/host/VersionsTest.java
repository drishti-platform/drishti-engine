/*
 * Drishti Engine · engines by contract, proven with a JDK only.
 *
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>.
 * All rights reserved.
 *
 * PROPRIETARY AND CONFIDENTIAL.
 *
 * This file is the confidential and proprietary property of Ashutosh Sinha.
 * Unauthorised copying, use, modification, distribution or disclosure of this
 * file, via any medium, is strictly prohibited except with the express prior
 * written permission of the copyright holder.
 *
 * See the LICENSE file in the root of this repository for the full terms.
 */
package com.ash.engine.host;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VersionsTest {

    @Test
    void theSameMajorAndAtLeastTheMinor() {
        assertThat(Versions.compatible("1.4", "1.0")).isTrue();
        assertThat(Versions.compatible("1.4", "1.4")).isTrue();
        assertThat(Versions.compatible("1.4.2", "1.3")).isTrue();
        assertThat(Versions.compatible("1.4", "1.5")).isFalse();
        assertThat(Versions.compatible("1.4", "2.0")).isFalse();
        assertThat(Versions.compatible("1.4", "0.9")).isFalse();
    }

    @Test
    void belowOneTheMinorIsTheMajor() {
        assertThat(Versions.compatible("0.1", "0.1")).isTrue();
        assertThat(Versions.compatible("0.2", "0.1")).isFalse();
        assertThat(Versions.compatible("0.1", "0.2")).isFalse();
        assertThat(Versions.compatible("0.9.2", "0.9.1")).isTrue();
    }
}
