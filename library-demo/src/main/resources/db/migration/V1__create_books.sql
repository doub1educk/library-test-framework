create table books
(
    id             bigserial primary key,
    title          varchar(255)             not null,
    author         varchar(255)             not null,
    isbn           varchar(13)              not null unique,
    published_year integer                  not null,
    available      boolean                  not null default true,
    created_at     timestamp with time zone not null
);

create index idx_books_author on books (lower(author));
