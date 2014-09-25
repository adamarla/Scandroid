package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import android.annotation.SuppressLint;
import android.util.Log;

public class QuestionManifest extends BaseManifest {
    
    public QuestionManifest(File dir, JSONArray items, Topic[] topics) throws Exception {
        super(dir, items, topics);
    }
    
    public Quij[] getFuzzle(String potd) {
        Question question = questionByIdMap.get(potd);
        question.setName("Q.1");
        String locn = question.getImgLocn();
        locn = locn.substring(0, locn.length()-1) + "0";
        question.setImgLocn(locn);
        
        long topicId = Long.parseLong(question.getId().split("\\.")[0]);
        Topic t = null;
        for (Topic topic : topics) {
            if (topicId == topic.id) {
                t = topic;
                break;
            }
        }
        Quij toReturn = new Quij(t.name, null, t.id, 0, PZL_TYPE, null);
        toReturn.add(question);
        toReturn.determineState();
        return new Quij[] { toReturn };
    }
    
    /**
     * Skip conditions:
     * == LOCKED     => Not attempted yet
     * == DOWNLOADED => Being worked on
     * >  CAPTURED   => Received (Sent & Graded box)
     * @param beforeState
     * @return
     */
    public Quij[] getQsns(int fromState, int toState) {
        HashMap<Long, Quij> quizByTopic = initialize(topics);
        Set<String> questionIds = questionByIdMap.keySet();
        Iterator<String> iterator = questionIds.iterator();
        
        long topicId; Quij quiz; Question question;
        while (iterator.hasNext()) {
            question = questionByIdMap.get(iterator.next());
            if (question.getState() > fromState ||
                question.getState() < toState) continue;
            
            topicId = Long.parseLong(question.getId().split("\\.")[0]);
            quiz = quizByTopic.get(topicId);
            question.setName(String.format("Q.%s", quiz.size()+1));
            quiz.add(question);
        }
        
        ArrayList<Quij> toReturn = new ArrayList<Quij>();
        Set<Long> quizIds = quizByTopic.keySet();
        Iterator<Long> iter = quizIds.iterator();
        while (iter.hasNext()) {
            quiz = quizByTopic.get(iter.next());
            if (quiz.size() > 0) {
                toReturn.add(quiz);
            }
        }
        
        return toReturn.toArray(new Quij[toReturn.size()]);
    }
    
    @Override
    public void parse(JSONArray items) throws Exception {
        if (items == null) return;
        
        JSONObject item;
        for (int i = 0; i < items.size(); i++) {
            item = (JSONObject)items.get(i);
            
            Question question = new Question(
                (String)item.get(NAME_KEY),
                (String)item.get(ID_KEY), 
                (Long)item.get(QUESN_ID_KEY),
                (String)item.get(SBPRTS_ID_KEY), 
                (String)item.get(IMG_PATH_KEY),
                ((Long)item.get(IMG_SPAN_KEY)).shortValue());            
            question.setOutOf(((Long)item.get(OUT_OF_KEY)).shortValue());
            question.setExaminer(((Long)item.get(EXAMINER_KEY)).intValue());
            
            boolean notYetReceived = item.get(PZL_KEY) == null;            
            if (notYetReceived) {
                String qsnState = state.getProperty(question.getId());
                if (qsnState != null) {
                    question.setPgMap(qsnState.split(",")[0]);
                    if (question.getPgMap("").matches("0*")) {
                        question.setState(DOWNLOADED);
                    } else {
                        question.setState(qsnState.split(",")[1].equals("0") ?
                            CAPTURED : SENT);
                    }
                } else {
                    question.setState(DOWNLOADED);
                }
            } else {
                state.remove(question.getId());
                question.setPuzzle((Boolean)item.get(PZL_KEY));
                question.setScanLocn((String)item.get(SCANS_KEY));
                int[] grId = question.getGRId();
                grId[0] = ((Long)item.get(GR_ID_KEY)).intValue();
                question.setGRId(grId);
                float marks = ((Long)item.get(MARKS_KEY)).floatValue();
                Log.d(TAG, question.getId() + " " + marks);
                question.setState(marks < 0 ? RECEIVED : GRADED);
                question.setMarks(marks);
            }
            questionByIdMap.put(question.getId(), question);
        }
    }
    
    @SuppressLint("UseSparseArrays")
    private HashMap<Long, Quij> initialize(Topic[] topics) {
        HashMap<Long, Quij> quizByTopic = new HashMap<Long, Quij>();
        for (Topic topic : topics) {
            quizByTopic.put(topic.id, 
                new Quij(topic.name, null, topic.id, 0, QSN_TYPE, null));
        }
        return quizByTopic;
    }    

    public enum Op {
        EQUAL,
        NEQUAL,
        GEQUAL,
        LEQUAL,
        GT,
        LT
    }
    
}

