package top.daoha.domain.agent.service.auto.step;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将大模型返回的自然语言监督结果转换为稳定的流程控制决策。
 */
final class QualityDecisionParser {

    private static final Pattern QUALITY_DECISION_PATTERN = Pattern.compile(
            "是否通过\\s*[:：]\\s*(PASS|FAIL|OPTIMIZE)",
            Pattern.CASE_INSENSITIVE
    );

    private QualityDecisionParser() {
    }

    static QualityDecision parse(String supervisionResult) {
        if (supervisionResult == null || supervisionResult.isBlank()) {
            return QualityDecision.UNKNOWN;
        }

        String normalizedResult = supervisionResult
                .replace("*", "")
                .replace("_", "")
                .replace("`", "")
                .trim();

        Matcher matcher = QUALITY_DECISION_PATTERN.matcher(normalizedResult);
        if (!matcher.find()) {
            return QualityDecision.UNKNOWN;
        }

        try {
            return QualityDecision.valueOf(matcher.group(1).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return QualityDecision.UNKNOWN;
        }
    }
}
