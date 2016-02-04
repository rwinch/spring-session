<!doctype html>
<html>
    <head>
        <title>Log In</title>
        <meta name="layout" content="main">
    </head>
    <body>
        <form action="./login" method="post">
           <p>
               <label for="username">Username</label>
               <input type="text" name="username" id="username"/>
           </p>
           <p>
               <label for="password">Password</label>
               <input type="password" name="password" id="password"/>
           </p>
           <div>
               <input type="submit" value="Log In"/>
           </div>
           <input type="hidden" name="_csrf" value="${_csrf.token}"/>
        </form>
    </body>
</html>
