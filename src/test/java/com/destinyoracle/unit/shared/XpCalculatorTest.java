package com.destinyoracle.unit.shared;

import com.destinyoracle.entity.CardStage;
import com.destinyoracle.entity.DestinyCard;
import com.destinyoracle.repository.DestinyCardRepository;
import com.destinyoracle.shared.xp.XpCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class XpCalculatorTest {

    @Mock private DestinyCardRepository cardRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private XpCalculator xpCalculator;

    private DestinyCard card(CardStage stage, int currentXp) {
        DestinyCard c = DestinyCard.builder()
            .id(UUID.randomUUID())
            .currentStage(stage)
            .build();
        c.setCurrentXp(currentXp);
        c.setXpToNextStage(100);
        return c;
    }

    @Test
    void awardTaskStepXp_addsXp() {
        DestinyCard c = card(CardStage.storm, 50);
        when(cardRepo.findById(c.getId())).thenReturn(Optional.of(c));

        xpCalculator.awardTaskStepXp(c.getId());

        assertThat(c.getCurrentXp()).isEqualTo(65); // 50 + 15
        assertThat(c.getCurrentStage()).isEqualTo(CardStage.storm); // not advanced
    }

    @Test
    void awardXp_reachesThreshold_advancesStage() {
        DestinyCard c = card(CardStage.storm, 90);
        when(cardRepo.findById(c.getId())).thenReturn(Optional.of(c));

        xpCalculator.awardTaskStepXp(c.getId()); // 90 + 15 = 105 >= 100

        assertThat(c.getCurrentStage()).isEqualTo(CardStage.fog);
        assertThat(c.getCurrentXp()).isEqualTo(5); // 105 - 100 = 5 overflow
        assertThat(c.getStageAdvancedAt()).isNotNull();
    }

    @Test
    void awardXp_nullCardId_doesNothing() {
        xpCalculator.awardTaskStepXp(null);
        verifyNoInteractions(cardRepo);
    }

    @Test
    void awardXp_legendStage_doesNotAdvanceFurther() {
        DestinyCard c = card(CardStage.legend, 9000);
        c.setXpToNextStage(999999);
        when(cardRepo.findById(c.getId())).thenReturn(Optional.of(c));

        xpCalculator.awardTaskStepXp(c.getId());

        assertThat(c.getCurrentStage()).isEqualTo(CardStage.legend); // stays
        assertThat(c.getCurrentXp()).isEqualTo(9015);
    }
}
