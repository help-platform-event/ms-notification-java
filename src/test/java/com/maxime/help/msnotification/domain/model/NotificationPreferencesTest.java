package com.maxime.help.msnotification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class NotificationPreferencesTest {

    private static final NotificationPreferences ALL_OFF =
            new NotificationPreferences(false, false, false, false, false, false, false);

    @ParameterizedTest
    @EnumSource(NotificationCategory.class)
    void defaults_allowEverything(NotificationCategory category) {
        assertThat(NotificationPreferences.defaults().allows(category)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = NotificationCategory.class, names = {"ACCOUNT", "SECURITY"})
    void transactionalCategories_areAlwaysAllowed(NotificationCategory category) {
        assertThat(ALL_OFF.allows(category)).isTrue();
    }

    @Test
    void masterSwitchOff_blocksEveryNonTransactionalCategory() {
        NotificationPreferences masterOff = new NotificationPreferences(false, true, true, true, true, true, true);

        assertThat(NotificationCategory.values())
                .filteredOn(category -> !category.isTransactional())
                .allSatisfy(category -> assertThat(masterOff.allows(category)).isFalse());
    }

    @Test
    void eachCategorySwitch_onlyControlsItsOwnCategory() {
        NotificationPreferences onlyActivity = new NotificationPreferences(true, true, false, false, false, false, false);

        assertThat(onlyActivity.allows(NotificationCategory.EVENT_ACTIVITY)).isTrue();
        assertThat(onlyActivity.allows(NotificationCategory.EVENT_MESSAGES)).isFalse();
        assertThat(onlyActivity.allows(NotificationCategory.JUDGMENTS)).isFalse();
    }
}
