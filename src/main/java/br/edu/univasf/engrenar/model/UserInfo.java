package br.edu.univasf.engrenar.model;
public record UserInfo(long id,String name,String username,Role role){
    @Override public String toString(){return name+" ("+username+", "+role+")";}
}
