-- Google account id ("sub" claim). Stable even if the person changes their Gmail address.
ALTER TABLE user_account ADD COLUMN google_subject text UNIQUE;
