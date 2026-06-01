package org.example;

import org.example.mapper.MaterialProblemMapper;
import org.example.mapper.QuestionMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
        
        List<Question> allQuestions = questionMapper.findAllFromTotal();
        List<Question> wrongQuestions = new ArrayList<>();
        
        for (Integer questionId : mistakeList) {
            for (Question q : allQuestions) {
                if (questionId.equals(q.getId())) {
                    wrongQuestions.add(q);
                    break;
                }
            }
        }
        return wrongQuestions;
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

}