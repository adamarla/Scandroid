package com.gradians.collect;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class QuestionManifest extends BaseManifest {
    
    public QuestionManifest(File dir, Topic[] topics) throws Exception {
        super(dir, topics);
    }
    
    @Override
    public Quij[] all() {
        HashMap<Long, Quij> quizByTopic = initialize(topics);
        Set<String> questionIds = questionByIdMap.keySet();
        Iterator<String> iterator = questionIds.iterator();
        
        long topicId; Quij quiz; Question question;
        while (iterator.hasNext()) {
            question = questionByIdMap.get(iterator.next());
            
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
        Collections.sort(toReturn);
        return toReturn.toArray(new Quij[toReturn.size()]);
    }    
    
    @Override
    public void parse(JSONArray items, boolean remote) throws Exception {
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
            
            if (item.get(AVAILBL_KEY) != null) {
                if (!(Boolean)item.get(AVAILBL_KEY)) {
                    questionByIdMap.remove(question.getId());
                    state.remove(question.getId());
                    continue;
                }
            }
            
            if (!(Boolean)item.get(HAS_ANSWER_KEY)) continue;
            
            question.setOutOf(((Long)item.get(OUT_OF_KEY)).shortValue());
            question.setExaminer(item.get(EXAMINER_KEY) == null ? 0 : 
                ((Long)item.get(EXAMINER_KEY)).intValue());
            question.setHasCodex((Boolean)item.get(HAS_CODEX_KEY));
            question.setHasAns((Boolean)item.get(HAS_ANSWER_KEY));
            
            if (item.containsKey(SOLN_KEY) && item.get(SOLN_KEY) != null)
                question.setBotSoln((Boolean)item.get(SOLN_KEY));
            
            if (item.containsKey(ANS_KEY) && item.get(ANS_KEY) != null)
                question.setBotAns((Boolean)item.get(ANS_KEY));
            
            if (item.containsKey(GUESSED_KEY) && item.get(GUESSED_KEY) != null)
                question.setGuess(((Long)item.get(GUESSED_KEY)).intValue());
            
            if (item.containsKey(GR_ID_KEY) && item.get(GR_ID_KEY) != null) {
                if (remote)
                    question.setGRId(new int[] {((Long)item.get(GR_ID_KEY)).intValue()});
                else
                    question.setGRId((String)item.get(GR_ID_KEY));
            }
            
            if (item.containsKey(DIRTY_KEY) && item.get(DIRTY_KEY) != null)
                question.setDirty((Boolean)item.get(DIRTY_KEY));
            
            short qState = DOWNLOADED;
            boolean noScanReceived = item.get(SCANS_KEY) == null;            
            if (noScanReceived) {
                String qsnState = state.getProperty(question.getId());
                if (qsnState != null) {
                    question.setPgMap(qsnState.split(",")[0]);
                    if (!question.getPgMap("").matches("0*")) {
                        qState = qsnState.split(",")[1].equals("0") ?
                            CAPTURED : SENT;
                    }
                }
            } else {
                state.remove(question.getId());
                question.setPuzzle((Boolean)item.get(PZL_KEY));
                question.setScanLocn((String)item.get(SCANS_KEY));
                float marks = remote ?
                    ((Long)item.get(MARKS_KEY)).floatValue() : 
                    ((Double)item.get(MARKS_KEY)).floatValue();
                qState = marks < 0 ? RECEIVED : GRADED;
                question.setMarks(marks);
            }            
            question.setState(qState);
            
            if (questionByIdMap.containsKey(question.getId())) {
                question.setImgLocn(questionByIdMap.get(question.getId()).getImgLocn());
            }
            
            questionByIdMap.put(question.getId(), question);
        }
    }
    
    private HashMap<Long, Quij> initialize(Topic[] topics) {
        HashMap<Long, Quij> quizByTopic = new HashMap<Long, Quij>();
        for (Topic topic : topics) {
            quizByTopic.put(topic.id, 
                new Quij(topic.name, null, topic.id, 0, QSN_TYPE, null));
        }
        return quizByTopic;
    }    
    
}

