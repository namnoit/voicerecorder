package com.namnoit.voicerecorder;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class ListDialogAdapter extends BaseAdapter {
    private static class Holder {
        ImageView icon;
        TextView text;
    }

    private Context context;
    private ArrayList<DialogMenuItem> list;

    public ListDialogAdapter(Context c){
        context = c;
        list = new ArrayList<>();
        list.add(new DialogMenuItem(R.drawable.ic_share,context.getResources().getString(R.string.menu_share)));
        list.add(new DialogMenuItem(R.drawable.ic_archive,context.getResources().getString(R.string.export)));
        list.add(new DialogMenuItem(R.drawable.ic_info,context.getResources().getString(R.string.details)));
        list.add(new DialogMenuItem(R.drawable.ic_delete,context.getResources().getString(R.string.delete)));
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int position) {
        return list.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Holder holder = new Holder();

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.item_menu_dialog, parent, false);
            holder.icon = convertView.findViewById(R.id.icon_item_dialog);
            holder.text= convertView.findViewById(R.id.text_item_dialog);
            convertView.setTag(holder);
        } else {
            holder = (Holder) convertView.getTag();
        }
        holder.icon.setImageResource(list.get(position).getIcon());
        holder.text.setText(list.get(position).getText());
        return convertView;
    }
}

class DialogMenuItem{
    private int icon;
    private String text;

    public int getIcon() {
        return icon;
    }

    public String getText() {
        return text;
    }

    public DialogMenuItem(int ic, String t){
        icon = ic;
        text = t;
    }
}
