package com.recipebookpro.ui.recipe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.recipebookpro.R;

import java.util.List;

import coil.Coil;
import coil.request.ImageRequest;

public class StickerAdapter extends RecyclerView.Adapter<StickerAdapter.ViewHolder> {

    public interface OnStickerClickListener {
        void onStickerClick(String url);
    }

    private final List<String> urls;
    private final OnStickerClickListener listener;

    public StickerAdapter(List<String> urls, OnStickerClickListener listener) {
        this.urls = urls;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sticker, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = urls.get(position);
        
        // Restore layout params for recycled views
        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        holder.itemView.setLayoutParams(lp);
        
        // Clear previous image to avoid flickering during recycle
        holder.ivSticker.setImageDrawable(null);
        holder.ivSticker.setVisibility(View.VISIBLE);

        ImageRequest request = new ImageRequest.Builder(holder.itemView.getContext())
                .data(url)
                .target(holder.ivSticker)
                .listener(new ImageRequest.Listener() {
                    @Override
                    public void onError(@NonNull ImageRequest request, @NonNull coil.request.ErrorResult result) {
                        holder.ivSticker.setVisibility(View.GONE);
                        ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
                        lp.width = 0;
                        lp.height = 0;
                        holder.itemView.setLayoutParams(lp);
                    }
                })
                .crossfade(200)
                .placeholder(R.drawable.ic_cook)
                .diskCacheKey(url)
                .memoryCacheKey(url)
                .build();
        Coil.imageLoader(holder.itemView.getContext()).enqueue(request);

        holder.itemView.setOnClickListener(v -> listener.onStickerClick(url));
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivSticker;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSticker = itemView.findViewById(R.id.ivStickerItem);
        }
    }
}
