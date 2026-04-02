package org.example.controller;

import org.example.common.Result;
import org.example.Question;            // 注意：这里没有 .entity，直接导入
import org.example.QuestionService;     // 注意：这里没有 .service，直接导入
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @RestController：表示这是一个控制器，返回的数据直接作为 JSON
// @RequestMapping("/api")：统一前缀，例如访问地址为 http://localhost:8080/api/addQuestion
@RestController
@RequestMapping("/api")
public class QuestionController {

    // 1. 注入 Service
    @Autowired
    private QuestionService questionService;

    // ==========================================
    // 接口 1: 添加题目
    // Apifox: POST 添加题目
    // ==========================================
    @PostMapping("/addQuestion")
    public Result<String> addQuestion(@RequestBody Question question) {
        // @RequestBody: 接收前端传来的 JSON 数据并自动转换为 Question 对象
        try {
            questionService.addQuestion(question);
            return Result.success("题目添加成功");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("添加失败：" + e.getMessage());
        }
    }

    // ==========================================
    // 接口 2: 删除题目
    // Apifox: DELETE 删除题目
    // ==========================================
    @PostMapping("/delete") // 建议用 PostMapping 配合 Body，或者保持 DeleteMapping 也可以
    public Result<String> deleteQuestion(@RequestBody Question question) {
        // 1. 从对象里取出 ID
        Integer id = question.getQuestion_id();

        try {
            questionService.deleteQuestionById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败");
        }
    }

    // ==========================================
    // 接口 3: 修改题目
    // Apifox: PATCH 修改题目
    // ==========================================
    @PatchMapping("/update")
    public Result<String> updateQuestion(@RequestBody Question question) {
        try {
            questionService.updateQuestion(question);
            return Result.success("修改成功");
        } catch (Exception e) {
            return Result.error("修改失败");
        }
    }

    // ==========================================
    // 接口 4: 查询题目 (列表)
    // Apifox: GET 查询题目
    // ==========================================
    @GetMapping("/list")
    public Result<List<Question>> listQuestions() {
        // 调用 Service 获取所有题目
        List<Question> list = questionService.getAllQuestions();
        return Result.success(list);
    }

    // ==========================================
    // 接口 5: 提交答案
    // Apifox: PUT 提交答案
    // ==========================================
    @PutMapping("/submit")
    public Result<String> submitAnswer(@RequestBody Question userAnswer) {
        // 这里假设前端传过来一个包含 id 和 userAnswer 的对象
        try {
            boolean isCorrect = questionService.checkAnswer(userAnswer);
            if (isCorrect) {
                return Result.success("回答正确！");
            } else {
                return Result.error("回答错误，再试一次。");
            }
        } catch (Exception e) {
            return Result.error("提交出错");
        }
    }

    // ==========================================
    // 接口 6: 错题查询
    // Apifox: GET 错题查询
    // ==========================================
    @GetMapping("/wrong")
    public Result<List<Question>> getWrongQuestions() {
        // 假设 Service 有一个方法专门查错题
        List<Question> wrongList = questionService.getWrongQuestions();
        return Result.success(wrongList);
    }
}