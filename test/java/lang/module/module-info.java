
module M @ 1.0
    provides M1 @ 2.0, M2 @ 2.1
{
    requires optional local N @ 9.0, P @ >=9.1;
    requires private Q @ 5.11;
    permits A, B;
    class M.X.Y;
}
