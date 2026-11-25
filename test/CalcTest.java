import fr.debitperf.Calc;
public class CalcTest {
  static int fails=0;
  static void eq(String n,double a,double b){ if(Math.abs(a-b)>1e-6){System.out.println("ECHEC "+n+": "+a+" != "+b);fails++;} else System.out.println("ok "+n+" = "+a); }
  static void eq(String n,String a,String b){ if(!a.equals(b)){System.out.println("ECHEC "+n+": '"+a+"' != '"+b+"'");fails++;} else System.out.println("ok "+n+" = "+a); }
  public static void main(String[] x){
    Calc c=new Calc(); c.volume=500;c.durMin=240;c.factor=20;
    eq("ml/h 500ml/4h",c.mlh(),125); eq("gtt/min 500ml/4h x20",c.gtt(),125*20/60.0);
    eq("periode",c.periodSec(),60/(125*20/60.0));
    c.factor=60; eq("micro-goutte : gtt = ml/h",c.gtt(),125);
    c.factor=20; c.setMlh(100); eq("100 ml/h -> duree",c.durMin,300);
    c.volume=1000;c.durMin=1440; eq("1L/24h",c.gtt(),1000/24.0*20/60);
    eq("vol 500+",Calc.stepVolume(500,1),550); eq("vol 100-",Calc.stepVolume(100,-1),90);
    eq("vol 100+",Calc.stepVolume(100,1),150); eq("vol 1000+",Calc.stepVolume(1000,1),1100);
    eq("vol min",Calc.stepVolume(10,-1),10);
    eq("dur 60+",Calc.stepDuration(60,1),75); eq("dur 60-",Calc.stepDuration(60,-1),55);
    eq("dur 247+ (aligne)",Calc.stepDuration(247,1),255); eq("dur 247-",Calc.stepDuration(247,-1),240);
    eq("debit 125+",Calc.stepRate(125,1),130); eq("debit 41,7+",Calc.stepRate(41.7,1),42);
    eq("facteur 20->60",Calc.nextFactor(20,1),60); eq("facteur 10->20",Calc.nextFactor(10,1),20);
    eq("facteur 20<-",Calc.nextFactor(20,-1),10);
    eq("verdict ok",Calc.verdict(42,41.7),0); eq("verdict rapide",Calc.verdict(46,41.7),1);
    eq("verdict tres rapide",Calc.verdict(50,41.7),2); eq("verdict lent",Calc.verdict(38,41.7),-1);
    eq("verdict tres lent",Calc.verdict(30,41.7),-2);
    long[] t={0,1500,3000,4500}; eq("mesure 1 goutte/1,5 s",Calc.measuredRate(t,4),40);
    eq("mesure 2 taps",Double.isNaN(Calc.measuredRate(t,2))?1:0,1);
    eq("num 41,67",Calc.num(41.666),"42"); eq("num 8,33",Calc.num(8.333),"8,3"); eq("num 0",Calc.num(0),"--");
    eq("duree 240",Calc.duration(240),"4 h 00"); eq("duree 45",Calc.duration(45),"45 min"); eq("duree 1445",Calc.duration(1445),"24 h 05");
    System.out.println(fails==0?"TOUS LES TESTS PASSENT":fails+" ECHEC(S)"); System.exit(fails);
  }
}
