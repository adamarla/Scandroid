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
        this.notifyDataSetChanged();
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
        
        Quij quiz = (Quij)getItem(position);
        
        TextView tv = (TextView)convertView.findViewById(R.id.tvQuiz);
        tv.setTag(position);
        tv.setText(quiz.toString());

        TextView tvTotal = (TextView)convertView.findViewById(R.id.tvTotal);
        if (quiz.getState() != GRADED)
            tvTotal.setText(String.valueOf(quiz.size()));
        else
            tvTotal.setText(String.format("%2.1f/%2d", quiz.getScore(), quiz.getMax()));
        
//        if (quiz.getState() == NOT_YET_BILLED) {
//            tvTotal.setBackgroundResource(R.drawable.blue_background);
//        } else if (quiz.getState() == NOT_YET_COMPLETED) {
//            tvTotal.setBackgroundResource(R.drawable.gray_background);
//        } else if (quiz.getState() == NOT_YET_SENT) {    
//            tvTotal.setBackgroundResource(R.drawable.lt_green_background);
//        } else if (quiz.getState() == NOT_YET_GRADED) {
//            tvTotal.setBackgroundResource(R.drawable.gray_background);
//        } else if (quiz.getState() == GRADED) {
//            tvTotal.setBackgroundResource(R.drawable.green_background);
//            tvTotal.setText(String.format("%2.1f/%2d", quiz.getScore(), quiz.getMax()));
//        }
        
        return convertView;
    }
    
    private Quij[] quizzes;
    private LayoutInflater inflater;

}
