package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.mapper.MaterialProblemMapper;
import org.example.mapper.QuestionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuestionService {

    @Autowired
    private QuestionMapper questionMapper;

    @Autowired
    private MaterialProblemMapper materialProblemMapper;

    public List<Question> getAllQuestions() {
        return questionMapper.findAll();
    }

    public Question getQuestionById(Integer id) {
        return questionMapper.findById(id);
    }

    public void addQuestion(Question question) {
        questionMapper.insert(question);
    }

    public void deleteQuestionById(Integer id) {
        questionMapper.deleteById(id);
    }

    public void updateQuestion(Question question) {
        questionMapper.update(question);
    }

    public boolean checkAnswer(Question userAnswer) {
        Question question = questionMapper.findById(userAnswer.getQuestion_id());
        if (question != null) {
            String correctAnswer = question.getCorrect_answer();
            String userSelected = userAnswer.getSelected_answer();
            System.out.println("正确答案: " + correctAnswer + ", 用户选择: " + userSelected);
            return correctAnswer != null && correctAnswer.equals(userSelected);
        }
        return false;
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public List<Integer> getWrongQuestionIds(Integer dataId) {
        try {
            Map<String, Object> userData = jdbcTemplate.queryForObject(
                    "SELECT mistake_list_id FROM user_data WHERE data_id = ?",
                    (rs, row) -> {
                        Map<String, Object> map = new HashMap<>();
                        java.sql.Array sqlArray = rs.getArray("mistake_list_id");
                        List<Integer> list = new ArrayList<>();
                        if (sqlArray != null) {
                            Integer[] arr = (Integer[]) sqlArray.getArray();
                            if (arr != null) {
                                list = Arrays.asList(arr);
                            }
                        }
                        map.put("mistake_list_id", list);
                        return map;
                    }, dataId);
            
            List<Integer> mistakeList = (List<Integer>) userData.get("mistake_list_id");
            return mistakeList != null ? mistakeList : new ArrayList<>();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    public List<Question> getWrongQuestions(Integer dataId) {
        List<Integer> mistakeList = getWrongQuestionIds(dataId);

        if (mistakeList == null || mistakeList.isEmpty()) {
            return new ArrayList<>();
        }

        // 获取用户 ID
        Integer userId = null;
        try {
            userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE data_id = ?", Integer.class, dataId);
        } catch (Exception e) { /* ignore */ }

        // 查 question_status
        Map<String, Object> questionStatus = loadQuestionStatus(dataId);

        // 批量查错误次数
        Map<Integer, Integer> wrongCountMap = new HashMap<>();
        if (userId != null && !mistakeList.isEmpty()) {
            wrongCountMap = loadWrongCounts(userId, mistakeList);
        }

        List<Question> allQuestions = questionMapper.findAllFromTotal();
        List<Question> wrongQuestions = new ArrayList<>();

        for (Integer questionId : mistakeList) {
            for (Question q : allQuestions) {
                if (questionId.equals(q.getId())) {
                    // 填 SRS 标签
                    Map<String, Object> srs = getSrsForQuestion(questionStatus, questionId);
                    if (srs != null) {
                        int n = srs.get("n") != null ? ((Number) srs.get("n")).intValue() : 0;
                        q.setSrsLabel(getSrsLabel(n));
                    } else {
                        q.setSrsLabel("待复习");
                    }
                    // 填错误次数
                    q.setWrongCount(wrongCountMap.getOrDefault(questionId, 0));
                    wrongQuestions.add(q);
                    break;
                }
            }
        }
        return wrongQuestions;
    }

    private Map<String, Object> loadQuestionStatus(Integer dataId) {
        try {
            String json = jdbcTemplate.queryForObject(
                "SELECT question_status::text FROM user_data WHERE data_id = ?",
                String.class, dataId);
            if (json == null || json.isEmpty()) return new HashMap<>();
            return new ObjectMapper().readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> getSrsForQuestion(Map<String, Object> statusMap, Integer questionId) {
        Object entry = statusMap.get(String.valueOf(questionId));
        if (entry instanceof Map) {
            return (Map<String, Object>) entry;
        }
        return null;
    }

    private String getSrsLabel(int n) {
        switch (n) {
            case 0: return "待复习";
            case 1: return "正确1/3";
            case 2: return "正确2/3";
            default: return "已掌握";
        }
    }

    private Map<Integer, Integer> loadWrongCounts(Integer userId, List<Integer> questionIds) {
        Map<Integer, Integer> result = new HashMap<>();
        if (questionIds.isEmpty()) return result;

        StringBuilder sql = new StringBuilder(
            "SELECT question_id, COUNT(*) AS cnt FROM problems_detail WHERE user_id = ? AND statue = -1 AND question_id IN (");
        for (int i = 0; i < questionIds.size(); i++) {
            sql.append(i == 0 ? "?" : ",?");
        }
        sql.append(") GROUP BY question_id");

        Object[] params = new Object[1 + questionIds.size()];
        params[0] = userId;
        for (int i = 0; i < questionIds.size(); i++) {
            params[i + 1] = questionIds.get(i);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params);
        for (Map<String, Object> row : rows) {
            Number qid = (Number) row.get("question_id");
            Number cnt = (Number) row.get("cnt");
            result.put(qid.intValue(), cnt.intValue());
        }
        return result;
    }
    
    private Question findQuestionByIdFromAllTables(Integer questionId) {
        Question question = questionMapper.findByTotalId(questionId);
        if (question != null) return question;
        
        question = questionMapper.findById(questionId);
        if (question != null) return question;
        
        question = questionMapper.findBySpeechId(questionId);
        if (question != null) return question;
        
        question = questionMapper.findByQuantitativeId(questionId);
        if (question != null) return question;
        
        question = questionMapper.findByCommonSenseId(questionId);
        if (question != null) return question;
        
        question = questionMapper.findByDataAnalysisId(questionId);
        if (question != null) return question;
        
        question = questionMapper.findByPoliticalId(questionId);
        if (question != null) return question;
        
        return null;
    }

    // ===================== 5大模块方法（完整写好）=====================
    public List<Question> getLogical(){
        return questionMapper.listLogical();
    }

    public List<Question> getCommonSense(){
        return questionMapper.listCommonSense();
    }

    public List<Question> getSpeech(){
        return questionMapper.listSpeech();
    }

    public List<Question> getQuantitative(){
        return questionMapper.listQuantitative();
    }

    public List<Question> getDataAnalysis(){
        return questionMapper.listDataAnalysis();
    }
    public List<Question> getPolitical(){
        return questionMapper.listPoliticalTheory();
    }

    public List<Question> getAllFromTotal() {
        List<Question> questions = questionMapper.findAllFromTotal();

        List<Integer> materialIds = new ArrayList<>();
        for (Question q : questions) {
            if (Boolean.TRUE.equals(q.getHas_material()) && q.getMaterial_id() != null) {
                materialIds.add(q.getMaterial_id());
            }
        }

        Map<Integer, List<MaterialProblem>> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<MaterialProblem> allProblems = materialProblemMapper.findAll();
            for (MaterialProblem mp : allProblems) {
                materialMap.computeIfAbsent(mp.getMaterial_id(), k -> new ArrayList<>()).add(mp);
            }
        }

        for (Question q : questions) {
            if (q.getMaterial_id() != null) {
                q.setMaterialProblems(materialMap.getOrDefault(q.getMaterial_id(), new ArrayList<>()));
            }
        }

        return questions;
    }

    public List<Question> getRandomQuestions(Integer count) {
        List<Question> questions = questionMapper.findRandomQuestions(count);

        List<Integer> materialIds = new ArrayList<>();
        for (Question q : questions) {
            if (Boolean.TRUE.equals(q.getHas_material()) && q.getMaterial_id() != null) {
                materialIds.add(q.getMaterial_id());
            }
        }

        Map<Integer, List<MaterialProblem>> materialMap = new HashMap<>();
        if (!materialIds.isEmpty()) {
            List<MaterialProblem> allProblems = materialProblemMapper.findAll();
            for (MaterialProblem mp : allProblems) {
                materialMap.computeIfAbsent(mp.getMaterial_id(), k -> new ArrayList<>()).add(mp);
            }
        }

        for (Question q : questions) {
            if (q.getMaterial_id() != null) {
                q.setMaterialProblems(materialMap.getOrDefault(q.getMaterial_id(), new ArrayList<>()));
            }
        }

        return questions;
    }

    public List<Question> searchQuestions(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return questionMapper.findRandomQuestions(Math.min(limit, 20));
        }
        return questionMapper.searchQuestions(keyword.trim(), Math.max(limit, 1));
    }

}