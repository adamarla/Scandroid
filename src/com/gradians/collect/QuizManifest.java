package com.gradians.collect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class QuizManifest implements IConstants {
    
    public QuizManifest(File manifests, JSONArray items) throws Exception {
        parse(manifests, items);
    }
    
    public void update(Question[] questions) {
        Question question;
        for (Question q : questions) {
            question = questionByIdMap.get(q.getId());
            question.setPgMap(q.getPgMap());
            question.setSentState(q.getSentState());
            question.setState(q.getState());
            stateMap.setProperty(q.getId(), q.toString());
        }
        
        for (Quij quiz : quizzes)
            quiz.determineState();
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
    
    public void commit() throws Exception {
        stateMap.store(new FileOutputStream(stateFile), null);
    }
    
    private void parse(File stateFile, JSONArray items) throws Exception {
        this.stateFile = stateFile;
        
        quizzes = new Quij[items.size()];        
        questionByIdMap = new HashMap<String, Question>();
        
        Properties lastState = new Properties();
        lastState.load(new FileInputStream(stateFile));
        
        stateMap = new Properties();
        for (int i = 0; i < items.size(); i++) {
            JSONObject quizItem = (JSONObject) items.get(i);
            quizzes[i] = new Quij((
                (String)quizItem.get(QUIZ_NAME_KEY)).replace(",", " "),
                (String)quizItem.get(QUIZ_PATH_KEY), 
                (Long)quizItem.get(QUIZ_ID_KEY),
                ((Long)quizItem.get(QUIZ_PRICE_KEY)).intValue(),
                (String)quizItem.get(QUIZ_LAYOUT_KEY));
            
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

                if (item.get(GR_ID_KEY) != null) {
                    question.setGRId((String)item.get(GR_ID_KEY));
                    question.setScanLocn((String)item.get(SCANS_KEY));
                    question.setOutOf(((Long)item.get(OUT_OF_KEY)).shortValue());
                    question.setExaminer(item.get(EXAMINER_KEY) == null ? 
                        0 : ((Long)item.get(EXAMINER_KEY)).intValue());
                    question.setFdbkMarker(item.get(FDBK_MRKR_KEY) == null ? 
                        0 : (Long)item.get(FDBK_MRKR_KEY));
                    question.setHintMarker(item.get(HINT_MRKR_KEY) == null ? 
                        0 : (Long)item.get(HINT_MRKR_KEY));
                    
                    boolean allPartsReceived = true;
                    for (String s : question.getScanLocn()) {
                        if (s.equals("")) {
                            allPartsReceived = false;
                            break;
                        }
                    }
                    
                    if (!allPartsReceived) {
                        // check local state
                        String qsnState = lastState.getProperty(question.getId());
                        if (qsnState != null) {
                            String[] tokens = qsnState.split(",");
                            
                            if (!tokens[0].contains("0")) question.setState(CAPTURED);
                            if (!tokens[1].contains("0")) question.setState(SENT);                            
                            question.setPgMap(tokens[0]);
                            question.setSentState(tokens[1]);
                        }                        
                    } else {
                        float marks = item.get(MARKS_KEY) == null ? -1f : 
                            ((Double)item.get(MARKS_KEY)).floatValue();
                        question.setState(marks < 0 ? RECEIVED : GRADED);
                        question.setMarks(marks);
                    }
                }
                quizzes[i].add(question);
                questionByIdMap.put(question.getId(), question);
            }
            quizzes[i].determineState();
            quizzes[i].updatePgMaps();
        }
    }
        
    private File stateFile;
    private Properties stateMap;
    
    private HashMap<String, Question> questionByIdMap;
    private Quij[] quizzes;

}