package vn.nghichtien.chienky.pve.service;

import org.junit.jupiter.api.Test;
import vn.nghichtien.chienky.pve.domain.PveCampaignStage;

import static org.junit.jupiter.api.Assertions.*;

class PveCampaignCatalogTest {

    private final PveCampaignCatalog catalog = new PveCampaignCatalog();

    @Test
    void shouldExposeTenOrderedCampaignStages() {
        assertEquals(10, catalog.getAll().size());

        for (int i = 0; i < catalog.getAll().size(); i++) {
            PveCampaignStage stage = catalog.getAll().get(i);
            assertEquals(i + 1, stage.stageNumber());
            assertEquals(3, stage.enemyHeroCodes().size());
            assertTrue(stage.goldReward().signum() > 0);
            assertTrue(stage.playerExpReward().signum() > 0);
        }
    }

    @Test
    void shouldReturnRequestedStage() {
        PveCampaignStage stage = catalog.get(1);

        assertEquals("CAMPAIGN_01", stage.stageCode());
        assertEquals("Bước chân tu tiên", stage.stageName());
        assertThrows(IllegalArgumentException.class, () -> catalog.get(0));
        assertThrows(IllegalArgumentException.class, () -> catalog.get(11));
    }
}
