import bcrypt
pairs = [
    ('admin123', '$2b$10$wjck23sU1vJVneq.EpXB1u2tpklMMLRFXIOYc2baJRxxabanN3e1q'),
    ('user123', '$2b$10$hjZAvDA8IgNnwbNLVzXMg.7JdC7/R4H2G578tg1HS6a4F4BDei2jq'),
    ('cinema123', '$2b$10$W/vxBf.H1kPeY987ZbZw9ePpVv.q9Y7UmzU1zKFGAFMUJ1BCNpK8G'),
]
for pw, h in pairs:
    print(pw, '->', bcrypt.checkpw(pw.encode(), h.encode()))
