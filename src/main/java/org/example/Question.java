package org.example;

// 对应 JSON 文件里的每一个大括号 { ... }
public class Question {
    private Integer question_id;
    private String title;
    private String category_id;
    private String difficulty;      // 对应 "medium"
    private String year;            // 对应 "2025"
    private String source;          // 对应 "公务员行测真题"
    private String status;          // 对应 "published"

    // 【关键修改】所有嵌套在 "question_content" 里的数据，都由这个对象统一接收
    private QuestionContent question_content;

    // ===================== 只加这 3 个字段 =====================
    private String correct_answer;
    private String answer_analysis;
    private String tips;
    // ==========================================================

    // 👇 新增的字段：用来接收前端传来的用户选择的答案 👇
    private String selected_answer;

    // --- Getter 和 Setter 方法 ---

    public Integer getQuestion_id() {
        return question_id;
    }

    public void setQuestion_id(Integer question_id) {
        this.question_id = question_id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory_id() {
        return category_id;
    }

    public void setCategory_id(String category_id) {
        this.category_id = category_id;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // QuestionContent 的 Getter 和 Setter
    public QuestionContent getQuestion_content() {
        return question_content;
    }

    public void setQuestion_content(QuestionContent question_content) {
        this.question_content = question_content;
    }

    // ===================== 只补这 3 个 Get/Set =====================
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
    // ==============================================================

    // selected_answer 的 Getter 和 Setter
    public String getSelected_answer() {
        return selected_answer;
    }

    public void setSelected_answer(String selected_answer) {
        this.selected_answer = selected_answer;
    }
}