package org.example;

import org.example.mapper.QuestionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuestionService {

    @Autowired
    private QuestionMapper questionMapper;

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

    public List<Question> getWrongQuestions() {
        return List.of();
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

}