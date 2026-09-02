package top.daoha.domain.agent.service.auto.step;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static top.daoha.domain.agent.service.auto.step.QualityDecision.FAIL;
import static top.daoha.domain.agent.service.auto.step.QualityDecision.OPTIMIZE;
import static top.daoha.domain.agent.service.auto.step.QualityDecision.PASS;
import static top.daoha.domain.agent.service.auto.step.QualityDecision.UNKNOWN;

public class Step3QualitySupervisorNodeTest {

    @Test
    public void shouldParsePlainPassDecision() {
        assertEquals(PASS, QualityDecisionParser.parse("是否通过: PASS"));
    }

    @Test
    public void shouldParseMarkdownOptimizeDecision() {
        assertEquals(OPTIMIZE, QualityDecisionParser.parse("**是否通过:** OPTIMIZE"));
    }

    @Test
    public void shouldParseFullWidthColonAndMarkdownStatus() {
        assertEquals(FAIL, QualityDecisionParser.parse("是否通过：**FAIL**"));
    }

    @Test
    public void shouldTreatMissingOrMalformedDecisionAsUnknown() {
        assertEquals(UNKNOWN, QualityDecisionParser.parse("检查结果需要继续优化"));
        assertEquals(UNKNOWN, QualityDecisionParser.parse(""));
        assertEquals(UNKNOWN, QualityDecisionParser.parse(null));
    }
}
