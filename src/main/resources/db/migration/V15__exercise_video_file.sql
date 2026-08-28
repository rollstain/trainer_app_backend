alter table exercises
    add column video_media_file_id uuid references media_files (id);
