
module com.foo.bar @ 1.2.3_04-5a
    provides com.foo.baz @ 2.0, com.foo.bez @ 3.4a-9
{
    requires private local edu.mit.biz @ >=1.1;
    requires optional org.tim.boz @ >=2.2-2;
    permits com.foo.buz, com.oof.byz;
    class com.foo.bar.Main;
}
