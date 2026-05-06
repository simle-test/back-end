package org.example.entity;

public class UserData {
    private Integer dataId;
    private Integer finished;
    private Integer mistakeNumbers;
    private Integer correctNumbers;
    private Integer numberDay;
    private Integer[] mistakeListId;
    private Integer[] correctLisId;

    public UserData() {
    }

    public Integer getDataId() {
        return dataId;
    }

    public void setDataId(Integer dataId) {
        this.dataId = dataId;
    }

    public Integer getFinished() {
        return finished;
    }

    public void setFinished(Integer finished) {
        this.finished = finished;
    }

    public Integer getMistakeNumbers() {
        return mistakeNumbers;
    }

    public void setMistakeNumbers(Integer mistakeNumbers) {
        this.mistakeNumbers = mistakeNumbers;
    }

    public Integer getCorrectNumbers() {
        return correctNumbers;
    }

    public void setCorrectNumbers(Integer correctNumbers) {
        this.correctNumbers = correctNumbers;
    }

    public Integer getNumberDay() {
        return numberDay;
    }

    public void setNumberDay(Integer numberDay) {
        this.numberDay = numberDay;
    }

    public Integer[] getMistakeListId() {
        return mistakeListId;
    }

    public void setMistakeListId(Integer[] mistakeListId) {
        this.mistakeListId = mistakeListId;
    }

    public Integer[] getCorrectLisId() {
        return correctLisId;
    }

    public void setCorrectLisId(Integer[] correctLisId) {
        this.correctLisId = correctLisId;
    }
}