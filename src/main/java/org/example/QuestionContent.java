package org.example;

import java.util.Map;

// 对应你 JSON 里的 "question_content" 对象
public class QuestionContent {

    private String image;

    // 对应 "option": { "A": "...", "B": "..." }
    private Map<String, String> option;

    // 对应 "correct_answer": "C"
    private String correct_answer;

    // 对应 "answer_analysis": "..."
    private String answer_analysis;

    // 对应 "tips": "..."
    private String tips;

    // 必须要有无参构造函数（Jackson 读取 JSON 需要）
    public QuestionContent() {}

    // Getter 和 Setter 方法

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Map<String, String> getOption() {
        return option;
    }

    public void setOption(Map<String, String> option) {
        this.option = option;
    }

    public String getCorrect_answer() {
        return correct_answer;
    }

    public void setCorrect_answer(String correct_answer) {
        this.correct_answer = correct_answer;
    }

    public String getAnswer_analysis() {
        return answer_analysis;
    }

    public void setAnswer_analysis(String answer_analysis) {
        this.answer_analysis = answer_analysis;
    }

    public String getTips() {
        return tips;
    }

    public void setTips(String tips) {
        this.tips = tips;
    }
}