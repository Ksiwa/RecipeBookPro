package com.recipebookpro.ui.book;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textview.MaterialTextView;
import com.recipebookpro.R;

public class CoverPageFragment extends Fragment {

    private static final String ARG_OWNER = "owner";
    private static final String ARG_COUNT = "count";

    public static CoverPageFragment newInstance(String owner, int count) {
        CoverPageFragment fragment = new CoverPageFragment();
        Bundle args = new Bundle();
        args.putString(ARG_OWNER, owner);
        args.putInt(ARG_COUNT, count);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cover_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        MaterialTextView tvOwner = view.findViewById(R.id.tvCoverOwner);
        MaterialTextView tvCount = view.findViewById(R.id.tvCoverCount);
        MaterialTextView tvSwipeHint = view.findViewById(R.id.tvCoverSwipeHint);

        tvOwner.setText(args.getString(ARG_OWNER, ""));
        tvCount.setText(getString(R.string.recipe_count_label, args.getInt(ARG_COUNT, 0)));
        tvSwipeHint.setText(R.string.swipe_to_open);
    }
}
