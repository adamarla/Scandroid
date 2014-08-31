package com.gradians.collect;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class QuizListAdapter extends BaseAdapter implements IConstants {
    
    public QuizListAdapter(Context context, Quij[] quizzes) {
        this.inflater = LayoutInflater.from(context);
        this.quizzes = quizzes;
    }
        
    public void update(int quizPosn, Question[] questions) {
        Quij quiz = quizzes[quizPosn];
        Question q = null;
        for (int i = 0; i < questions.length; i++) {
            q = quiz.get(i);
            q.setPgMap(questions[i].getPgMap());
            q.setState(questions[i].getState());
        }
        quiz.determineState();
        notifyDataSetChanged();
    }    

    @Override
    public int getCount() {
        return quizzes.length;
    }

    @Override
    public Object getItem(int position) {
        return quizzes[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null) {
            convertView = inflater.inflate(R.layout.layout_quiz, parent, false);
        }
        
        int completed = 0, graded = 0, total = 0;
        Quij quiz = (Quij)getItem(position);
        Question[] questions = quiz.getQuestions();
        for (Question q : questions ) {
            for (int pg : q.getPgMap()) if (pg != 0) completed++;
            total += q.getPgMap().length;
            if (q.getState() == GRADED) graded++;
        }
        
        TextView tv = (TextView)convertView.findViewById(R.id.tvQuiz);
        tv.setTag(position);
        tv.setText(quiz.toString());

        TextView tvTotal = (TextView)convertView.findViewById(R.id.tvTotal);
        if (quiz.getState() == NOT_YET_GRADED) {
            tvTotal.setText(String.format("%2d%%", (int)(graded*100/quiz.size())));
        } else if (quiz.getState() == GRADED) {
            tvTotal.setText(String.format("%2d%%", (int)(quiz.getScore()*100/quiz.getMax())));
        } else {
            tvTotal.setText(String.format("%2d%%", (int)(completed*100/total)));
        }
        
        return convertView;
    }
    
    private Quij[] quizzes;
    private LayoutInflater inflater;

}
