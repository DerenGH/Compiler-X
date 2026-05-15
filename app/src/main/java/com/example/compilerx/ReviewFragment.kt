package com.example.compilerx

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ReviewFragment : Fragment() {

    companion object {
        fun newInstance(userAnswers: List<UserAnswer>): ReviewFragment {
            val fragment = ReviewFragment()
            val args = Bundle()
            args.putSerializable("userAnswers", ArrayList(userAnswers))
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_review, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val answers = arguments?.getSerializable("userAnswers") as? ArrayList<UserAnswer> ?: arrayListOf()

        val rv = view.findViewById<RecyclerView>(R.id.rvReview)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

            inner class Holder(v: View) : RecyclerView.ViewHolder(v) {
                val qText: TextView = v.findViewById(R.id.tvQuestionNum)
                val uAns: TextView = v.findViewById(R.id.tvUserAnswer)
                val cAns: TextView = v.findViewById(R.id.tvCorrectAnswer)
            }

            override fun onCreateViewHolder(p: ViewGroup, t: Int) = Holder(
                LayoutInflater.from(p.context).inflate(R.layout.item_review, p, false)
            )

            override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
                val item = answers[p]
                val holder = h as Holder
                holder.qText.text = "${p + 1}. ${item.question.text}"
                holder.uAns.text = "Your Answer: " + (if (item.selectedIdx == -1) "Skipped" else item.question.options[item.selectedIdx])
                holder.cAns.text = "Correct: ${item.question.options[item.question.correctIdx]}"

                holder.uAns.setTextColor(if (item.isCorrect) 0xFF7EE787.toInt() else 0xFFFF7B72.toInt())
            }

            override fun getItemCount() = answers.size
        }
    }
}