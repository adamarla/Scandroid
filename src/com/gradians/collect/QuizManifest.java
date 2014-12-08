package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class QuizManifest extends BaseManifest {
    
    public QuizManifest(File dir, Topic[] topics) throws Exception {
        super(dir, topics);
    }

    @Override
    public void update(Question[] questions) {
        super.update(questions);
        for (Quij quiz : quizzes)
            quiz.determineState();
    }
    
    @Override
    public Quij[] all() {
        ArrayList<Quij> items = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            items.add(quiz);
        }
        return items.toArray(new Quij[items.size()]);
    }

    public Quij[] getInboxItems() {
        ArrayList<Quij> items = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            if (quiz.getState() < NOT_YET_GRADED)
                items.add(quiz);
        }
        return items.toArray(new Quij[items.size()]);
    }
    
    public Quij[] getOutboxItems() {
        ArrayList<Quij> items = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            if (quiz.getState() == NOT_YET_GRADED)
                items.add(quiz);
        }
        return items.toArray(new Quij[items.size()]);
    }
    
    public Quij[] getGradedItems() {
        ArrayList<Quij> items = new ArrayList<Quij>();
        for (Quij quiz : quizzes) {
            if (quiz.getState() > NOT_YET_GRADED)
                items.add(quiz);
        }
        return items.toArray(new Quij[items.size()]);
    }
    
    @Override
    public void parse(JSONArray items, boolean remote) throws Exception {        
        quizzes = new Quij[items.size()];
        
        for (int i = 0; i < items.size(); i++) {
            JSONObject quizItem = (JSONObject) items.get(i);
            quizzes[i] = new Quij((
                (String)quizItem.get(QUIZ_NAME_KEY)).replace(",", " "),
                (String)quizItem.get(QUIZ_PATH_KEY), 
                (Long)quizItem.get(QUIZ_ID_KEY),
                ((Long)quizItem.get(QUIZ_PRICE_KEY)).intValue(),
                GR_TYPE, (String)quizItem.get(QUIZ_LAYOUT_KEY));
            
            JSONArray questions = (JSONArray)quizItem.get(QUESTIONS_KEY);
            for (int j = 0; j < questions.size(); j++) {
                JSONObject item = (JSONObject)questions.get(j);
                
                Question question = new Question((
                    (String)item.get(NAME_KEY)).replace("-", ""),
                    (String)item.get(ID_KEY),
                    (Long)item.get(QUESN_ID_KEY),
                    (String)item.get(SBPRTS_ID_KEY),
                    (String)item.get(IMG_PATH_KEY),
                    ((Long)item.get(IMG_SPAN_KEY)).shortValue());
                
                question.setHasCodex((Boolean)item.get(HAS_CODEX_KEY));
                question.setHasAns((Boolean)item.get(HAS_ANSWER_KEY));
                
                if (item.get(GR_ID_KEY) != null) {
                    question.setGRId((String)item.get(GR_ID_KEY));
                    question.setScanLocn((String)item.get(SCANS_KEY));
                    question.setOutOf(((Long)item.get(OUT_OF_KEY)).shortValue());
                    question.setExaminer(item.get(EXAMINER_KEY) == null ? 
                        0 : ((Long)item.get(EXAMINER_KEY)).intValue());
                    question.setFdbkMarker(item.get(FDBK_MRKR_KEY) == null ? 
                        0 : (Long)item.get(FDBK_MRKR_KEY));
                    
                    boolean notYetReceived = question.getScanLocn()[0] == null;
                    if (notYetReceived) {
                        String qsnState = state.getProperty(question.getId());
                        if (qsnState != null) {
                            question.setPgMap(qsnState.split(",")[0]);
                            question.setState(qsnState.split(",")[1].equals("0")?
                                CAPTURED : SENT);
                        } else {
                            question.setState(DOWNLOADED);
                        }
                    } else {
                        state.remove(question.getId());
                        float marks = ((Double)item.get(MARKS_KEY)).floatValue();
                        question.setState(marks < 0 ? RECEIVED : GRADED);
                        question.setMarks(marks);
                    }
                }
                quizzes[i].add(question);
                questionByIdMap.put(question.getId(), question);
            }
            quizzes[i].determineState();
        }
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public String toJSONArray() {
        JSONArray array = new JSONArray();
        for (Quij quiz : quizzes) {
            array.add(quiz);
        }
        return array.toJSONString();
    }
    
    private Quij[] quizzes;


}