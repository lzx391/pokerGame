package com.example.mgdemoplus.moderation.impl;

import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.moderation.DpTextNormalize;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DpSensitiveWordServiceImpl implements DpSensitiveWordService {

    private static final Logger log = LoggerFactory.getLogger(DpSensitiveWordServiceImpl.class);
    private static final String WORD_LIST = "moderation/sensitive-words.txt";

    private volatile List<String> words = List.of();

    @PostConstruct
    void loadWordList() {
        List<String> loaded = new ArrayList<>();
        try {
            ClassPathResource resource = new ClassPathResource(WORD_LIST);
            if (!resource.exists()) {
                log.warn("sensitive word list missing: classpath:{}", WORD_LIST);
                words = List.of();
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String w = line.trim();
                    if (w.isEmpty() || w.startsWith("#")) {
                        continue;
                    }
                    loaded.add(w);
                }
            }
            loaded.sort(Comparator.comparingInt(String::length).reversed());
            words = List.copyOf(loaded);
            log.info("loaded {} sensitive words from classpath:{}", words.size(), WORD_LIST);
        } catch (Exception e) {
            log.error("failed to load sensitive words from classpath:{}", WORD_LIST, e);
            words = List.of();
        }
    }

    @Override
    public boolean containsSensitive(String text) {
        if (text == null || text.isBlank() || words.isEmpty()) {
            return false;
        }
        String compact = DpTextNormalize.compactForMatch(text);
        if (compact.isEmpty()) {
            return false;
        }
        for (String word : words) {
            String w = DpTextNormalize.compactForMatch(word);
            if (!w.isEmpty() && compact.contains(w)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String maskForChat(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return text;
        }
        char[] chars = text.toCharArray();
        boolean[] masked = new boolean[chars.length];
        for (String word : words) {
            if (word == null || word.isEmpty()) {
                continue;
            }
            int from = 0;
            int hit;
            while (from < text.length() && (hit = indexOfIgnoreCase(text, word, from)) >= 0) {
                int end = hit + word.length();
                for (int i = hit; i < end && i < masked.length; i++) {
                    masked[i] = true;
                }
                from = hit + 1;
            }
        }
        StringBuilder out = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; i++) {
            out.append(masked[i] ? '*' : chars[i]);
        }
        return out.toString();
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        if (needle.isEmpty()) {
            return -1;
        }
        int max = haystack.length() - needle.length();
        for (int i = from; i <= max; i++) {
            if (regionMatchesIgnoreCase(haystack, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean regionMatchesIgnoreCase(String a, int aOffset, String b, int bOffset, int len) {
        for (int i = 0; i < len; i++) {
            char ca = a.charAt(aOffset + i);
            char cb = b.charAt(bOffset + i);
            if (ca == cb) {
                continue;
            }
            if (ca >= 'A' && ca <= 'Z' && ca + 32 == cb) {
                continue;
            }
            if (cb >= 'A' && cb <= 'Z' && cb + 32 == ca) {
                continue;
            }
            return false;
        }
        return true;
    }
}
