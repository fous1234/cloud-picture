package com.example.cloudpicture.ai.service;

import com.example.cloudpicture.ai.dto.response.ImageMetadataVO;
import com.example.cloudpicture.ai.dto.response.ImageModerationVO;
import com.example.cloudpicture.common.exception.BusinessException;
import com.example.cloudpicture.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 严格解析模型返回内容：只接受 JSON 对象，缺字段/类型错误/解析失败统一视为 AI 暂不可用；
 * 标签按契约清理（去空、去重、丢弃超 32 字符、最多 10 个且总长度不超过 512）
 */
final class ModelResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_INTRODUCTION_LENGTH = 512;
    private static final int MAX_TAG_LENGTH = 32;
    private static final int MAX_TAG_COUNT = 10;
    private static final int MAX_TAGS_TOTAL_LENGTH = 512;
    private static final int MAX_LABEL_LENGTH = 32;
    private static final int MAX_LABEL_COUNT = 8;

    private ModelResponseParser() {
    }

    static ImageMetadataVO parse(String content) {
        JsonNode root = readObject(content);
        JsonNode introductionNode = root.get("introduction");
        JsonNode tagsNode = root.get("tags");
        if (introductionNode == null || !introductionNode.isTextual() || tagsNode == null || !tagsNode.isArray()) {
            throw unavailable();
        }
        String introduction = introductionNode.asText().trim();
        if (introduction.length() > MAX_INTRODUCTION_LENGTH) {
            introduction = introduction.substring(0, MAX_INTRODUCTION_LENGTH);
        }
        return new ImageMetadataVO(introduction, cleanTags(tagsNode));
    }

    static ImageModerationVO parseModeration(String content) {
        JsonNode root = readObject(content);
        JsonNode verdictNode = root.get("verdict");
        JsonNode confidenceNode = root.get("confidence");
        JsonNode labelsNode = root.get("labels");
        if (verdictNode == null || !verdictNode.isTextual()
                || confidenceNode == null || !confidenceNode.isInt()
                || labelsNode == null || !labelsNode.isArray()) {
            throw unavailable();
        }
        String verdict = verdictNode.asText().trim();
        if (!"PASS".equals(verdict) && !"REVIEW".equals(verdict) && !"BLOCK".equals(verdict)) {
            throw unavailable();
        }
        int confidence = confidenceNode.asInt();
        if (confidence < 0 || confidence > 100) {
            throw unavailable();
        }
        return new ImageModerationVO(verdict, confidence, cleanLabels(labelsNode));
    }

    private static List<String> cleanLabels(JsonNode labelsNode) {
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode labelNode : labelsNode) {
            if (!labelNode.isTextual()) {
                throw unavailable();
            }
            String label = labelNode.asText().trim();
            if (label.isEmpty() || label.length() > MAX_LABEL_LENGTH || !seen.add(label)) {
                continue;
            }
            if (labels.size() >= MAX_LABEL_COUNT) {
                break;
            }
            labels.add(label);
        }
        return labels;
    }

    private static List<String> cleanTags(JsonNode tagsNode) {
        List<String> tags = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int totalLength = 0;
        for (JsonNode tagNode : tagsNode) {
            if (!tagNode.isTextual()) {
                throw unavailable();
            }
            String tag = tagNode.asText().trim();
            if (tag.isEmpty() || tag.length() > MAX_TAG_LENGTH || !seen.add(tag)) {
                continue;
            }
            if (tags.size() >= MAX_TAG_COUNT || totalLength + tag.length() > MAX_TAGS_TOTAL_LENGTH) {
                break;
            }
            tags.add(tag);
            totalLength += tag.length();
        }
        return tags;
    }

    private static JsonNode readObject(String content) {
        if (content == null || content.isBlank()) {
            throw unavailable();
        }
        try (JsonParser parser = MAPPER.getFactory().createParser(content)) {
            JsonNode root = MAPPER.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) {
                throw unavailable();
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(ErrorCode.AI_UNAVAILABLE);
    }
}
