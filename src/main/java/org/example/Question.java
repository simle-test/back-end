package org.example;

import java.util.List;

public class Question {
    private Integer id;
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
    private Boolean has_image;
    private String image_url;
    private Boolean has_material;
    private String content_text;
    private Integer material_id;
    private List<MaterialProblem> materialProblems;

    // SRS/错题统计相关（非数据库字段，接口返回时填充）
    private Integer wrongCount;
    private String srsLabel;

    // --- Getter 和 Setter 方法 ---

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

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

    public Boolean getHas_image() {
        return has_image;
    }

    public void setHas_image(Boolean has_image) {
        this.has_image = has_image;
    }

    public String getImage_url() {
        return image_url;
    }

    public void setImage_url(String image_url) {
        this.image_url = image_url;
    }

    public Boolean getHas_material() {
        return has_material;
    }

    public void setHas_material(Boolean has_material) {
        this.has_material = has_material;
    }

    public String getContent_text() {
        return content_text;
    }

    public void setContent_text(String content_text) {
        this.content_text = content_text;
    }

    public Integer getMaterial_id() {
        return material_id;
    }

    public void setMaterial_id(Integer material_id) {
        this.material_id = material_id;
    }

    public List<MaterialProblem> getMaterialProblems() {
        return materialProblems;
    }

    public void setMaterialProblems(List<MaterialProblem> materialProblems) {
        this.materialProblems = materialProblems;
    }

    public Integer getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(Integer wrongCount) {
        this.wrongCount = wrongCount;
    }

    public String getSrsLabel() {
        return srsLabel;
    }

    public void setSrsLabel(String srsLabel) {
        this.srsLabel = srsLabel;
    }
}