package com.finanzapp.app.ui.transactions;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.finanzapp.app.R;
import com.finanzapp.app.data.model.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends ArrayAdapter<Category> {
    private final List<Category> allCategories;
    private List<Category> filteredCategories;

    public CategoryAdapter(@NonNull Context context, @NonNull List<Category> categories) {
        super(context, 0, categories);
        this.allCategories = new ArrayList<>(categories);
        this.filteredCategories = new ArrayList<>(categories);
    }

    @Override
    public int getCount() {
        return filteredCategories.size();
    }

    @Nullable
    @Override
    public Category getItem(int position) {
        return filteredCategories.get(position);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_category_dropdown, parent, false);
        }

        Category category = getItem(position);
        if (category != null) {
            View colorView = convertView.findViewById(R.id.view_category_color);
            TextView nameText = convertView.findViewById(R.id.tv_category_name);

            nameText.setText(category.getName());
            
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            try {
                shape.setColor(Color.parseColor(category.getColor()));
            } catch (Exception e) {
                shape.setColor(Color.GRAY);
            }
            colorView.setBackground(shape);
        }

        return convertView;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<Category> suggestions = new ArrayList<>();

                if (constraint == null || constraint.length() == 0) {
                    suggestions.addAll(allCategories);
                } else {
                    String filterPattern = constraint.toString().toLowerCase().trim();
                    for (Category category : allCategories) {
                        if (category.getName().toLowerCase().contains(filterPattern)) {
                            suggestions.add(category);
                        }
                    }
                }

                results.values = suggestions;
                results.count = suggestions.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                filteredCategories = (List<Category>) results.values;
                notifyDataSetChanged();
            }

            @Override
            public CharSequence convertResultToString(Object resultValue) {
                return ((Category) resultValue).getName();
            }
        };
    }
}