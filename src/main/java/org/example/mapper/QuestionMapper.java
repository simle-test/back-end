package org.example.mapper;

import org.example.Question;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface QuestionMapper {

    List<Question> findAll();
    Question findById(Integer questionId);
    void insert(Question question);
    void deleteById(Integer questionId);
    void update(Question question);

    List<Question> listLogical();
    List<Question> listCommonSense();
    List<Question> listSpeech();
    List<Question> listQuantitative();
    List<Question> listDataAnalysis();
}