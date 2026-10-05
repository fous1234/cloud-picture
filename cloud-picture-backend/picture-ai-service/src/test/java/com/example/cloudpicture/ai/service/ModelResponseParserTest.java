package com.example.cloudpicture.ai.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.cloudpicture.ai.dto.response.ImageMetadataVO;
import com.example.cloudpicture.ai.dto.response.ImageModerationVO;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ModelResponseParserTest {

    @Test
    void parsesValidJsonAndTrimsIntroduction() {
        ImageMetadataVO vo = ModelResponseParser.parse("{\"introduction\":\" 一只猫 \",\"tags\":[\"猫\",\"宠物\"]}");
        assertEquals("一只猫", vo.getIntroduction());
        assertEquals(List.of("猫", "宠物"), vo.getTags());
    }

    @Test
    void ignoresExtraFields() {
        ImageMetadataVO vo = ModelResponseParser.parse("{\"introduction\":\"x\",\"tags\":[\"a\"],\"extra\":1}");
        assertEquals(List.of("a"), vo.getTags());
    }

    @Test
    void rejectsMarkdownFence() {
        assertUnavailable("```json\n{\"introduction\":\"x\",\"tags\":[]}\n```");
    }

    @Test
    void rejectsMissingFields() {
        assertUnavailable("{\"tags\":[\"a\"]}");
        assertUnavailable("{\"introduction\":\"x\"}");
    }

    @Test
    void rejectsWrongTypes() {
        assertUnavailable("{\"introduction\":12,\"tags\":[]}");
        assertUnavailable("{\"introduction\":\"x\",\"tags\":\"a\"}");
        assertUnavailable("{\"introduction\":\"x\",\"tags\":[1,2]}");
        assertUnavailable("[]");
    }

    @Test
    void rejectsBlankOrNonJson() {
        assertUnavailable("");
        assertUnavailable("   ");
        assertUnavailable("抱歉，我无法处理这张图片");
    }

    @Test
    void rejectsTrailingContentAfterJson() {
        assertUnavailable("{\"introduction\":\"x\",\"tags\":[]} trailing");
        assertUnavailable("{\"introduction\":\"x\",\"tags\":[]}{\"extra\":1}");
    }

    @Test
    void cleansTagsTrimDedupeAndDropBlank() {
        ImageMetadataVO vo = ModelResponseParser.parse(
                "{\"introduction\":\"x\",\"tags\":[\" 猫 \",\"\",\"  \",\"猫\",\"狗\",\"a\"]}");
        assertEquals(List.of("猫", "狗", "a"), vo.getTags());
    }

    @Test
    void dropsTagsLongerThan32() {
        String overlong = "a".repeat(33);
        ImageMetadataVO vo = ModelResponseParser.parse(
                "{\"introduction\":\"x\",\"tags\":[\"" + overlong + "\",\"ok\"]}");
        assertEquals(List.of("ok"), vo.getTags());
    }

    @Test
    void keepsAtMostTenTagsInOrder() {
        String tags = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> "\"t" + i + "\"")
                .collect(Collectors.joining(","));
        ImageMetadataVO vo = ModelResponseParser.parse("{\"introduction\":\"x\",\"tags\":[" + tags + "]}");
        assertEquals(10, vo.getTags().size());
        assertEquals("t1", vo.getTags().get(0));
        assertEquals("t10", vo.getTags().get(9));
    }

    @Test
    void truncatesIntroductionTo512() {
        String overlong = "字".repeat(600);
        ImageMetadataVO vo = ModelResponseParser.parse(
                "{\"introduction\":\"" + overlong + "\",\"tags\":[]}");
        assertEquals(512, vo.getIntroduction().length());
    }

    @Test
    void parsesValidModerationVerdict() {
        ImageModerationVO vo = ModelResponseParser.parseModeration(
                "{\"verdict\":\"BLOCK\",\"confidence\":95,\"labels\":[\" 色情低俗 \",\"暴力血腥\"]}");
        assertEquals("BLOCK", vo.getVerdict());
        assertEquals(95, vo.getConfidence());
        assertEquals(List.of("色情低俗", "暴力血腥"), vo.getLabels());
    }

    @Test
    void parsesPassVerdictWithEmptyLabels() {
        ImageModerationVO vo = ModelResponseParser.parseModeration(
                "{\"verdict\":\"PASS\",\"confidence\":88,\"labels\":[]}");
        assertEquals("PASS", vo.getVerdict());
        assertEquals(0, vo.getLabels().size());
    }

    @Test
    void rejectsInvalidModerationVerdict() {
        assertModerationUnavailable("{\"verdict\":\"MAYBE\",\"confidence\":50,\"labels\":[]}");
        assertModerationUnavailable("{\"verdict\":1,\"confidence\":50,\"labels\":[]}");
    }

    @Test
    void rejectsMissingOrWrongModerationTypes() {
        assertModerationUnavailable("{\"confidence\":50,\"labels\":[]}");
        assertModerationUnavailable("{\"verdict\":\"PASS\",\"labels\":[]}");
        assertModerationUnavailable("{\"verdict\":\"PASS\",\"confidence\":50}");
        assertModerationUnavailable("{\"verdict\":\"PASS\",\"confidence\":\"50\",\"labels\":[]}");
        assertModerationUnavailable("{\"verdict\":\"PASS\",\"confidence\":50,\"labels\":\"色情低俗\"}");
    }

    @Test
    void rejectsModerationConfidenceOutOfRange() {
        assertModerationUnavailable("{\"verdict\":\"PASS\",\"confidence\":-1,\"labels\":[]}");
        assertModerationUnavailable("{\"verdict\":\"PASS\",\"confidence\":101,\"labels\":[]}");
    }

    @Test
    void cleansModerationLabelsAndKeepsAtMostEight() {
        String labels = "[\" a \",\"\",\"a\",\"b\",\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]";
        ImageModerationVO vo = ModelResponseParser.parseModeration(
                "{\"verdict\":\"REVIEW\",\"confidence\":50,\"labels\":" + labels + "}");
        assertEquals(List.of("a", "b", "1", "2", "3", "4", "5", "6"), vo.getLabels());
    }

    @Test
    void rejectsNonTextModerationLabel() {
        assertModerationUnavailable("{\"verdict\":\"BLOCK\",\"confidence\":95,\"labels\":[1,2]}");
    }

    private static void assertModerationUnavailable(String content) {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> ModelResponseParser.parseModeration(content));
        assertEquals(ErrorCode.AI_UNAVAILABLE, ex.getErrorCode());
    }

    private static void assertUnavailable(String content) {
        BusinessException ex = assertThrows(BusinessException.class, () -> ModelResponseParser.parse(content));
        assertEquals(ErrorCode.AI_UNAVAILABLE, ex.getErrorCode());
    }
}
